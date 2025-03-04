// Switch to identity database
db = db.getSiblingDB('offer')

// Create indexes
// A single collection can have no more than 64 indexes.

// TapeAccessRequestReferential
db.TapeAccessRequestReferential.createIndex( { "unavailableArchiveIds" : 1 } )
db.TapeAccessRequestReferential.createIndex( { "objectIds" : 1 } )
db.TapeAccessRequestReferential.createIndex( { "expirationDate" : 1 } )
db.TapeAccessRequestReferential.createIndex( { "purgeDate" : 1 } )

// TapeArchiveReferential : No required index (other than _id)

// TapeCatalog
db.TapeCatalog.createIndex( { "library" : 1, "code" : 1 } )
db.TapeCatalog.createIndex( { "queue_state" : 1, "queue_message_type" : 1, "library" : 1, "tape_state": 1, "bucket": 1 } )

// TapeObjectReferential
db.TapeObjectReferential.createIndex( { "_id.containerName" : 1, "_id.objectName" : 1 } )

// TapeQueueMessage
db.TapeQueueMessage.createIndex( { "filePath" : 1, "queue_message_type" : 1 } )
db.TapeQueueMessage.createIndex( { "fileName" : 1, "queue_message_type" : 1 } )
db.TapeQueueMessage.createIndex( { "queue_state" : 1, "queue_message_type" : 1, "bucket": 1 } )
db.TapeQueueMessage.createIndex( { "queue_state" : 1, "queue_message_type" : 1, "tapeCode": 1 } )

