// ============================================================
// Chalo Backend — Firebase Admin SDK Initialization
// Used for: Auth token verification, FCM push, RTDB, Storage
// ============================================================

import admin from 'firebase-admin';
import config from './index';
import logger from './logger';
import path from 'path';
import fs from 'fs';

let firebaseApp: admin.app.App;

export function initializeFirebase(): admin.app.App {
  if (firebaseApp) return firebaseApp;

  try {
    const serviceAccountPath = path.resolve(config.firebase.serviceAccountPath);

    // In production, use the service account file
    // In development, fall back to application default credentials
    if (fs.existsSync(serviceAccountPath)) {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const serviceAccount = require(serviceAccountPath);

      firebaseApp = admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        databaseURL: config.firebase.databaseUrl,
        storageBucket: `${serviceAccount.project_id}.appspot.com`,
      });

      logger.info('Firebase Admin initialized with service account');
    } else if (config.isDev) {
      // Dev mode: initialize without credentials for local testing
      logger.warn('Firebase service account not found — running in dev mode without Firebase');
      firebaseApp = admin.initializeApp({
        projectId: 'chalo-dev-local',
      });
    } else {
      throw new Error(`Firebase service account file not found at: ${serviceAccountPath}`);
    }

    return firebaseApp;
  } catch (error) {
    logger.error('Failed to initialize Firebase Admin', { error });
    throw error;
  }
}

// Lazy getters — only access after initialization
export const getAuth = () => admin.auth(firebaseApp);
export const getFirestore = () => admin.firestore(firebaseApp);
export const getMessaging = () => admin.messaging(firebaseApp);
export const getDatabase = () => admin.database(firebaseApp); // Realtime DB
export const getStorage = () => admin.storage(firebaseApp);

export default { initializeFirebase, getAuth, getFirestore, getMessaging, getDatabase, getStorage };
