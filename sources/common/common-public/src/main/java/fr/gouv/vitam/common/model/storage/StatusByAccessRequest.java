/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2020)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL-C license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL-C license as
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
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL-C license and that you
 * accept its terms.
 */
package fr.gouv.vitam.common.model.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.annotation.Nonnull;

public final class StatusByAccessRequest {

    @JsonProperty("objectAccessRequest")
    private final AccessRequestReference accessRequestReference;
    @JsonProperty("accessRequestStatus")
    private final AccessRequestStatus accessRequestStatus;

    public StatusByAccessRequest(
        @Nonnull @JsonProperty("objectAccessRequest") AccessRequestReference accessRequestReference,
        @Nonnull @JsonProperty("accessRequestStatus") AccessRequestStatus accessRequestStatus) {

        this.accessRequestReference = accessRequestReference;
        this.accessRequestStatus = accessRequestStatus;
    }

    public AccessRequestReference getObjectAccessRequest() {
        return accessRequestReference;
    }

    public AccessRequestStatus getAccessRequestStatus() {
        return accessRequestStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StatusByAccessRequest that = (StatusByAccessRequest) o;
        return Objects.equal(accessRequestReference, that.accessRequestReference) &&
            accessRequestStatus == that.accessRequestStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(accessRequestReference, accessRequestStatus);
    }

    @Override
    public String toString() {
        return "StatusByObjectAccessRequest[" + accessRequestReference + "," + accessRequestStatus + "]";
    }
}
