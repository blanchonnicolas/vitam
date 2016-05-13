/*******************************************************************************
 * This file is part of Vitam Project.
 * 
 * Copyright Vitam (2012, 2015)
 *
 * This software is governed by the CeCILL 2.1 license under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/ or redistribute the software under the terms of the CeCILL license as
 * circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify
 * and redistribute granted by the license, users are provided only with a
 * limited warranty and the software's author, the holder of the economic
 * rights, and the successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with
 * loading, using, modifying and/or developing or reproducing the software by
 * the user in light of its specific status of free software, that may mean that
 * it is complicated to manipulate, and that also therefore means that it is
 * reserved for developers and experienced professionals having in-depth
 * computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling
 * the security of their systems and/or data to be ensured and, more generally,
 * to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.parser.request.parser;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.builder.request.construct.Insert;
import fr.gouv.vitam.builder.request.construct.Request;
import fr.gouv.vitam.builder.request.construct.configuration.GlobalDatas;
import fr.gouv.vitam.builder.request.construct.configuration.ParserTokens.GLOBAL;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;

/**
 * Insert Parser: [ {root}, {query}, {filter}, {data} ] or { $roots: root,
 * $query : query, $filter : filter, $data : data}
 *
 */
public class InsertParser extends RequestParser {

    VarNameInsertAdapter insertAdapter;
    /**
     * 
     */
    public InsertParser() {
        super();
        insertAdapter = new VarNameInsertAdapter(adapter);
    }

    /**
     * @param adapter 
     * 
     */
    public InsertParser(VarNameAdapter adapter) {
        super(adapter);
        insertAdapter = new VarNameInsertAdapter(adapter);
    }

    @Override
    protected Request getNewRequest() {
        return new Insert();
    }

    /**
     *
     * @param request
     *            containing a parsed JSON as [ {root}, {query}, {filter}, {data} ] or
     *            { $roots: root, $query : query, $filter : filter, $data :
     *            data}
     * @throws InvalidParseOperationException
     */
    public void parse(final JsonNode request) throws InvalidParseOperationException {
    	parseJson(request);
    	internalParseInsert();
    }

    /**
     *
     * @param request
     *            containing a JSON as [ {root}, {query}, {filter}, {data} ] or
     *            { $roots: root, $query : query, $filter : filter, $data :
     *            data}
     * @throws InvalidParseOperationException
     */
    public void parse(final String request) throws InvalidParseOperationException {
        parseString(request);
        internalParseInsert();
    }

	/**
	 * @throws InvalidParseOperationException
	 */
	private void internalParseInsert() throws InvalidParseOperationException {
		if (rootNode.isArray()) {
            // should be 4, but each could be empty ( '{}' )
            if (rootNode.size() > 3) {
                dataParse(rootNode.get(3));
            }
        } else {
            // not as array but composite as { $roots: root, $query : query,
            // $filter : filter, $data : data }
            dataParse(rootNode.get(GLOBAL.DATA.exactToken()));
        }
	}

    /**
     * {$data : { field: value, ... }
     *
     * @param rootNode
     * @throws InvalidParseOperationException
     */
    protected void dataParse(final JsonNode rootNode)
            throws InvalidParseOperationException {
    	if (rootNode == null) {
    		throw new InvalidParseOperationException(
                    "Parse in error for Insert: empty data");
    	}
        GlobalDatas.sanityValueCheck(rootNode.toString());
        // Fix varname using adapter
        // XXX FIXME Note: values are not changed. This shall be a specific computation
        // For instance: mavar : #id
        JsonNode newRootNode = insertAdapter.getFixedVarNameJsonNode(rootNode);
        try {
            ((Insert) request).setData(newRootNode);
        } catch (final Exception e) {
            throw new InvalidParseOperationException(
                    "Parse in error for Insert: " + rootNode, e);
        }
    }

    @Override
    public String toString() {
        return new StringBuilder().append(request.toString()).append("\n\tLastLevel: ").append(lastDepth).toString();
    }

    @Override
    public Insert getRequest() {
        return (Insert) request;
    }
}
