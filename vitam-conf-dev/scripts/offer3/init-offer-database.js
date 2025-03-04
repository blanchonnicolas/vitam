// Switch to identity database
db = db.getSiblingDB('offer3')

// Create indexes
// A single collection can have no more than 64 indexes.
db.OfferLog.createIndex( { "Container" : 1, "Sequence" : 1, "Time": -1 } )
db.CompactedOfferLog.createIndex( { "Container" : 1, "SequenceStart" : -1 } )
db.CompactedOfferLog.createIndex( { "Container" : 1, "SequenceEnd" : 1 } )

// Drop old indexes
db.OfferLog.dropIndex( { "container" : 1} )
db.OfferLog.dropIndex( { "Container" : 1} )
db.CompactedOfferLog.dropIndex( { "Container": 1, "SequenceStart": 1, "SequenceEnd": -1 } )

// For migration
db.OfferLog.dropIndex( { "Container" : 1, "Sequence" : 1 } )
