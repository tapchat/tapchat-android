/* MemorizingTrustManager - a TrustManager which asks the user about invalid
 *  certificates and memorizes their decision.
 *
 * Copyright (c) 2010 Georg Lukas <georg@op-co.de>
 *
 * MemorizingTrustManager.java contains the actual trust manager and interface
 * code to create a MemorizingActivity and obtain the results.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.tapchatapp.android.network.ssl;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import com.google.common.collect.Maps;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

public class MemorizingTrustManager implements X509TrustManager {
    public static final String DECISION_INTENT             = "de.duenndns.ssl.DECISION";
    public static final String DECISION_INTENT_APP         = DECISION_INTENT + ".app";
    public static final String DECISION_INTENT_ID          = DECISION_INTENT + ".decisionId";
    public static final String DECISION_INTENT_FINGERPRINT = DECISION_INTENT + ".fingerprint";
    public static final String DECISION_INTENT_CHOICE      = DECISION_INTENT + ".decisionChoice";

    private static final String TAG           = "MemorizingTrustManager";
    private static final String KEYSTORE_DIR  = "KeyStore";
    private static final String KEYSTORE_FILE = "KeyStore.bks";

    private static final Map<Integer, MTMDecision> sDecisions = Maps.newHashMap();
    private static int sLastDecisionId = 0;

    private Context          mContext;
    private Handler          mHandler;
    private X509TrustManager mDefaultTrustManager;
    private X509TrustManager mAppTrustManager;

    public static X509TrustManager[] getInstanceList(Context c) {
   		return new X509TrustManager[] { new MemorizingTrustManager(c) };
   	}

	public MemorizingTrustManager(Context context) {
		mContext = context;
		mHandler = new Handler();

        mDefaultTrustManager = getTrustManager(null);
        mAppTrustManager     = getTrustManager(loadAppKeyStore());
	}

	private X509TrustManager getTrustManager(KeyStore ks) {
		try {
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
			tmf.init(ks);
			for (TrustManager t : tmf.getTrustManagers()) {
				if (t instanceof X509TrustManager) {
					return (X509TrustManager)t;
				}
			}
            return null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

    private File getKeyStoreFile() {
        Application app = getApplication(mContext);
        File dir = app.getDir(KEYSTORE_DIR, Context.MODE_PRIVATE);
        return new File(dir, KEYSTORE_FILE);
    }

    private KeyStore loadAppKeyStore() {
        File file = getKeyStoreFile();
        try {
		    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(null, null);

            if (file.exists()) {
			    ks.load(new java.io.FileInputStream(file), "MTM".toCharArray());
            }

            return ks;

        } catch (Exception ex) {
            if (file.exists()) {
                file.delete();
            }
            throw new RuntimeException(ex);
        }
	}

	private void storeCert(X509Certificate[] chain) {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);

		    // Add all certs from chain to key store
			for (X509Certificate c : chain) {
                store.setCertificateEntry(c.getSubjectDN().toString(), c);
            }

		    // Overwrite existing keystore
            FileOutputStream stream = null;
            try {
                stream = new java.io.FileOutputStream(getKeyStoreFile());
                store.store(stream, "MTM".toCharArray());
            } finally {
                if (stream != null) {
                    stream.close();
                }
            }

		    // reload trust manager with new store
		    mAppTrustManager = getTrustManager(store);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
   		checkCertTrusted(chain, authType, false);
   	}

   	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
   		checkCertTrusted(chain, authType, true);
   	}

    public X509Certificate[] getAcceptedIssuers() {
        return mDefaultTrustManager.getAcceptedIssuers();
   	}

	private void checkCertTrusted(X509Certificate[] chain, String authType, boolean isServer) throws CertificateException {
        if (checkCertificate(mDefaultTrustManager, chain, authType, isServer)) {
            return;
        }

        if (checkCertificate(mAppTrustManager, chain, authType, isServer)) {
            return;
        }

        interact(chain);
    }

    private boolean checkCertificate(X509TrustManager manager, X509Certificate[] chain, String authType, boolean isServer) {
		try {
			if (isServer) {
				manager.checkServerTrusted(chain, authType);
            } else {
                manager.checkClientTrusted(chain, authType);
            }
            return true;
		} catch (CertificateException ae) {
            return false;
		}
	}

    private void interact(final X509Certificate[] chain) throws CertificateException {
   		final MTMDecision decision = createDecision();

        IntentFilter filter = new IntentFilter(DECISION_INTENT + "/" + mContext.getPackageName());
        BroadcastReceiver receiver = new BroadcastReceiver() {
            public void onReceive(Context ctx, Intent intent) {
                int decisionId = intent.getIntExtra(DECISION_INTENT_ID, MTMDecision.DECISION_INVALID);
                int choice = intent.getIntExtra(DECISION_INTENT_CHOICE, MTMDecision.DECISION_INVALID);

                MTMDecision decision = getDecision(decisionId);
                if (decision == null) {
                    Log.e(TAG, "interactResult: aborting due to stale decision reference!");
                    return;
                }

                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (decision) {
                    decision.state = choice;
                    decision.notify();
                }
            }
        };

        mContext.registerReceiver(receiver, filter);

   		mHandler.post(new Runnable() {
   			public void run() {
                Intent intent = new Intent(mContext, MemorizingActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   				intent.setData(Uri.parse(MemorizingTrustManager.class.getName() + "/" + decision.id));
                intent.putExtra(DECISION_INTENT_APP, mContext.getPackageName());
                intent.putExtra(DECISION_INTENT_ID, decision.id);
                intent.putExtra(DECISION_INTENT_FINGERPRINT, certHash(chain[0], "SHA-1"));
                mContext.startActivity(intent);
   			}
   		});

        //noinspection EmptyCatchBlock
        try {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (decision) {
                decision.wait();
            }
   		} catch (InterruptedException e) {
   		}

        mContext.unregisterReceiver(receiver);

   		switch (decision.state) {
   		    case MTMDecision.DECISION_ALWAYS:
                storeCert(chain);
   		    case MTMDecision.DECISION_ONCE:
   			    break;
   		    default:
   			    throw new CertificateException();
   		}
   	}

	private static String hexString(byte[] data) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < data.length; i++) {
			builder.append(String.format("%02x", data[i]));
			if (i < data.length - 1)
				builder.append(":");
		}
		return builder.toString();
	}

	private static String certHash(final X509Certificate cert, String digest) {
		try {
			MessageDigest md = MessageDigest.getInstance(digest);
			md.update(cert.getEncoded());
			return hexString(md.digest());
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

    private static MTMDecision createDecision() {
        synchronized (sDecisions) {
            sLastDecisionId++;
            MTMDecision decision = new MTMDecision(sLastDecisionId);
            sDecisions.put(sLastDecisionId, decision);
            return decision;
        }
    }

    private static MTMDecision getDecision(int decisionId) {
        synchronized (sDecisions) {
            return sDecisions.remove(decisionId);
        }
    }

    private static Application getApplication(Context context) {
        if (context instanceof Application) {
            return (Application)context;
        } else if (context instanceof Service) {
            return ((Service)context).getApplication();
        } else if (context instanceof Activity) {
            return ((Activity)context).getApplication();
        } else {
            throw new IllegalArgumentException("context must be either Activity or Service!");
        }
    }
}