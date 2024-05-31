const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const db = admin.database();
const storage = admin.storage();

exports.matchRandomUser = functions.database.ref('waitingUsers/{matchingType}/{uid}')
    .onCreate(async (snap, context) => {
        const newUser = snap.val();
        const newUid = context.params.uid;
        const matchingType = context.params.matchingType;

        const waitingUsersRef = db.ref(`waitingUsers/${matchingType}`);
        const waitingUserSnap = await waitingUsersRef.once('value');
        const waitingUsers = waitingUserSnap.val();

        console.log(`Waiting users for ${matchingType}: `, waitingUsers);

        if (waitingUsers && Object.keys(waitingUsers).length >= 2) {
            const uids = Object.keys(waitingUsers);

            let matchingUid = null;
            let commonInterest = null;
            if (matchingType === "interest") {
                for (const uid of uids) {
                    if (uid !== newUid) {
                        const matchingUser = waitingUsers[uid];
                        const newUserInterests = newUser.interests ? Object.values(newUser.interests) : [];
                        const userInterests = matchingUser.interests ? Object.values(matchingUser.interests) : [];

                        console.log(`New User Interests: ${newUserInterests}`);
                        console.log(`Matching User Interests: ${userInterests}`);

                        const commonInterests = newUserInterests.filter(value => userInterests.includes(value));

                        console.log(`Common Interests: ${commonInterests}`);

                        if (commonInterests.length > 0) {
                            matchingUid = uid;
                            commonInterest = commonInterests[0];
                            break;
                        }
                    }
                }
            } else {
                for (const uid of uids) {
                    if (uid !== newUid) {
                        matchingUid = uid;
                        break;
                    }
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
                updates[`users/${newUid}/currentChat`] = {
                    chatId: chatId,
                    commonInterest: commonInterest || ""
                };
                updates[`users/${matchingUid}/currentChat`] = {
                    chatId: chatId,
                    commonInterest: commonInterest || ""
                };
                updates[`waitingUsers/${matchingType}/${newUid}`] = null;
                updates[`waitingUsers/${matchingType}/${matchingUid}`] = null;

                console.log(`Matched users: ${newUid} and ${matchingUid} with chatId: ${chatId} and commonInterest: ${commonInterest}`);

                await db.ref().update(updates);
            } else {
                console.log("No matching user found.");
            }
        } else {
            console.log("Not enough users to match.");
        }
    });

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

        const notification = {
            message: "Chat ended",
            type: "chatEnded"
        };

        if (user1) {
            await db.ref(`users/${user1}/currentChat`).remove();
            await db.ref(`notifications/${user1}`).push(notification);
        }

        if (user2) {
            await db.ref(`users/${user2}/currentChat`).remove();
            await db.ref(`notifications/${user2}`).push(notification);
        }
    });
