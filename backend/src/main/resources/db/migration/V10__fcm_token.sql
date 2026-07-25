-- Android app is native Kotlin (FCM), not Expo — no expoPushToken existed to replace.
ALTER TABLE users ADD COLUMN fcm_token varchar(255);
