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
package fr.gouv.vitam.common.database.parser.request.single;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.database.builder.request.single.Delete;
import fr.gouv.vitam.common.database.builder.request.single.RequestSingle;
import fr.gouv.vitam.common.database.parser.request.adapter.VarNameAdapter;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;

/**
 * Delete Parser: { $query : query, $filter : multi }
 */
public class DeleteParserSingle extends RequestParserSingle {

    /**
     * @param adapter VarNameAdapter
     */
    public DeleteParserSingle(VarNameAdapter adapter) {
        super(adapter);
    }

    @Override
    protected RequestSingle getNewRequest() {
        return new Delete();
    }

    /**
     * @param request containing a parsed JSON as { $query : query, $filter : multi }
     * @throws InvalidParseOperationException if request could not parse to JSON
     */
    @Override
    public void parse(final JsonNode request) throws InvalidParseOperationException {
        parseJson(request);
    }

    @Override
    public String toString() {
        return new StringBuilder().append(request.toString()).toString();
    }

    @Override
    public Delete getRequest() {
        return (Delete) request;
    }
}
