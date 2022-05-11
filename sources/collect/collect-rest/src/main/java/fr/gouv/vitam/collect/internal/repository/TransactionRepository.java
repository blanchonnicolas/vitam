/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2022)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "https://cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.collect.internal.repository;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import fr.gouv.vitam.collect.internal.exception.CollectException;
import fr.gouv.vitam.collect.internal.model.TransactionModel;
import fr.gouv.vitam.common.database.server.mongodb.BsonHelper;
import fr.gouv.vitam.common.database.server.mongodb.MongoDbAccess;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * repository for collect entities  management in mongo.
 */
public class TransactionRepository {

    public static final String TRANSACTION_COLLECTION = "Transaction";
    public static final String ID = "_id";
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(TransactionRepository.class);

    private final MongoCollection<Document> transactionCollection;

    @VisibleForTesting
    public TransactionRepository(MongoDbAccess mongoDbAccess, String collectionName) {
        transactionCollection = mongoDbAccess.getMongoDatabase().getCollection(collectionName);
    }

    public TransactionRepository(MongoDbAccess mongoDbAccess) {
        this(mongoDbAccess, TRANSACTION_COLLECTION);
    }

    /**
     * create a transaction model
     *
     * @param transactionModel transaction model to create
     * @throws CollectException exception thrown in case of error
     */
    public void createTransaction(TransactionModel transactionModel) throws CollectException {
        LOGGER.debug("Transaction to create: {}", transactionModel);
        try {
            String transactionModelAsString = JsonHandler.writeAsString(transactionModel);
            transactionCollection.insertOne(Document.parse(transactionModelAsString));
        } catch (InvalidParseOperationException e) {
            throw new CollectException("Error when creating transaction: " + e);
        }
    }

    /**
     * replace a transaction model
     *
     * @param transactionModel transaction model to replace
     * @throws CollectException exception thrown in case of error
     */
    public void replaceTransaction(TransactionModel transactionModel) throws CollectException {
        LOGGER.debug("Transaction to replace: {}", transactionModel);
        try {
            String transactionModelAsString = JsonHandler.writeAsString(transactionModel);
            final Bson condition = and(eq(ID, transactionModel.getId()));
            transactionCollection.replaceOne(condition, Document.parse(transactionModelAsString));
        } catch (InvalidParseOperationException e) {
            throw new CollectException("Error when replacing transaction: " + e);
        }
    }

    /**
     * return transaction according to id
     *
     * @param id transaction id to find
     * @return Optional<TransactionModel>
     * @throws CollectException exception thrown in case of error
     */
    public Optional<TransactionModel> findTransaction(String id) throws CollectException {
        LOGGER.debug("Transaction id to find : {}", id);
        try {
            Document first = transactionCollection.find(Filters.eq(ID, id)).first();
            if (first == null) {
                return Optional.empty();
            }
            return Optional.of(BsonHelper.fromDocumentToObject(first, TransactionModel.class));
        } catch (InvalidParseOperationException e) {
            throw new CollectException("Error when searching transaction by id: " + e);
        }
    }

    /**
     * return transaction according to id
     *
     * @param id transaction id to find
     * @return Optional<TransactionModel>
     * @throws CollectException exception thrown in case of error
     */
    public Optional<TransactionModel> findTransactionByProjectId(String id) throws CollectException {
        LOGGER.debug("Transaction id to find : {}", id);
        try {
            Document first = transactionCollection.find(Filters.eq("ProjectId", id)).first();
            if (first == null) {
                return Optional.empty();
            }
            return Optional.of(BsonHelper.fromDocumentToObject(first, TransactionModel.class));
        } catch (InvalidParseOperationException e) {
            throw new CollectException("Error when searching transaction by project id: " + e);
        }
    }
}

