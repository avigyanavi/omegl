const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const db = admin.database();
const storage = admin.storage();

// Random Matching
exports.matchRandomUser = functions.database.ref('waitingUsers/random/{uid}')
    .onCreate(async (snap, context) => {
        const newUser = snap.val();
        const newUid = context.params.uid;

        const waitingUsersRef = db.ref('waitingUsers/random');
        const waitingUserSnap = await waitingUsersRef.once('value');
        const waitingUsers = waitingUserSnap.val();

        if (waitingUsers && Object.keys(waitingUsers).length >= 2) {
            const uids = Object.keys(waitingUsers);

            let matchingUid;
            for (const uid of uids) {
                if (uid !== newUid) {
                    matchingUid = uid;
                    break;
                }
            }

            if (matchingUid) {
                const chatId = db.ref('chats').push().key;

                const updates = {};
                updates[`chats/${chatId}`] = {
                    user1: newUid,
                    user2: matchingUid,
                    messages: {}
                };
                updates[`users/${newUid}/currentChat`] = chatId;
                updates[`users/${matchingUid}/currentChat`] = chatId;
                updates[`waitingUsers/random/${newUid}`] = null;
                updates[`waitingUsers/random/${matchingUid}`] = null;

                await db.ref().update(updates);
            }
        }
    });

// Chat Cleanup
exports.cleanUpChat = functions.database.ref('chats/{chatId}')
    .onDelete(async (snap, context) => {
        const chatId = context.params.chatId;
        const chatData = snap.val();

        const user1 = chatData.user1;
        const user2 = chatData.user2;

        const messages = chatData.messages;
        for (const messageId in messages) {
            const message = messages[messageId];
            if (message.type === 'image' || message.type === 'video') {
                const fileUrl = message.content;
                const filePath = fileUrl.split('/').pop().split('?')[0].split('%2F').join('/');
                await storage.bucket().file(filePath).delete();
            }
        }

        if (user1) {
            await db.ref(`users/${user1}/currentChat`).remove();
        }

        if (user2) {
            await db.ref(`users/${user2}/currentChat`).remove();
        }
    });
