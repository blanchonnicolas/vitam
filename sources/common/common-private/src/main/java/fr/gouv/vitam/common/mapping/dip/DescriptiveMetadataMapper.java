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
package fr.gouv.vitam.common.mapping.dip;

import fr.gouv.culture.archivesdefrance.seda.v2.CustodialHistoryType;
import fr.gouv.culture.archivesdefrance.seda.v2.DescriptiveMetadataContentType;
import fr.gouv.culture.archivesdefrance.seda.v2.EventType;
import fr.gouv.culture.archivesdefrance.seda.v2.LinkingAgentIdentifierType;
import fr.gouv.culture.archivesdefrance.seda.v2.ManagementHistoryDataType;
import fr.gouv.culture.archivesdefrance.seda.v2.ManagementHistoryType;
import fr.gouv.culture.archivesdefrance.seda.v2.MessageDigestBinaryObjectType;
import fr.gouv.culture.archivesdefrance.seda.v2.ReferencedObjectType;
import fr.gouv.culture.archivesdefrance.seda.v2.SignatureType;
import fr.gouv.culture.archivesdefrance.seda.v2.TextType;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.model.unit.ArchiveUnitHistoryModel;
import fr.gouv.vitam.common.model.unit.DescriptiveMetadataModel;
import fr.gouv.vitam.common.model.unit.EventTypeModel;
import fr.gouv.vitam.common.model.unit.LinkingAgentIdentifierTypeModel;
import fr.gouv.vitam.common.model.unit.ReferencedObjectTypeModel;
import fr.gouv.vitam.common.model.unit.SignatureTypeModel;
import fr.gouv.vitam.common.model.unit.SignedObjectDigestModel;
import org.apache.commons.collections.CollectionUtils;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static javax.xml.datatype.DatatypeFactory.newInstance;

/**
 * Map the object DescriptiveMetadataModel generated from Unit data base model To a jaxb object
 * DescriptiveMetadataContentType This help convert DescriptiveMetadataModel to xml using jaxb
 */
public class DescriptiveMetadataMapper {

    private final CustodialHistoryMapper custodialHistoryMapper = new CustodialHistoryMapper();
    private final ManagementMapper managementMapper = new ManagementMapper(new RuleMapper());

    /**
     * Map local DescriptiveMetadataModel to jaxb DescriptiveMetadataContentType
     *
     * @param metadataModel
     * @param historyListModel
     * @return a descriptive Metadata Content Type
     * @throws DatatypeConfigurationException
     */
    public DescriptiveMetadataContentType map(
        DescriptiveMetadataModel metadataModel,
        List<ArchiveUnitHistoryModel> historyListModel
    ) throws DatatypeConfigurationException {
        DescriptiveMetadataContentType dmc = new DescriptiveMetadataContentType();
        dmc.setAcquiredDate(metadataModel.getAcquiredDate());

        if (metadataModel.getAddressee() != null) {
            dmc.getAddressee().addAll(metadataModel.getAddressee());
        }
        dmc.getAny().addAll(TransformJsonTreeToListOfXmlElement.mapJsonToElement(metadataModel.getAny()));

        dmc.setCoverage(metadataModel.getCoverage());
        dmc.setCreatedDate(metadataModel.getCreatedDate());

        CustodialHistoryType custodialHistory = custodialHistoryMapper.map(metadataModel.getCustodialHistory());
        dmc.setCustodialHistory(custodialHistory);

        if (metadataModel.getDescription_() != null) {
            dmc.getDescription().addAll(metadataModel.getDescription_().getTextTypes());
        }

        if (metadataModel.getDescription() != null) {
            TextType description = new TextType();
            description.setValue(metadataModel.getDescription());
            dmc.getDescription().add(description);
        }

        dmc.setDescriptionLanguage(metadataModel.getDescriptionLanguage());
        dmc.setDescriptionLevel(metadataModel.getDescriptionLevel());
        dmc.setDocumentType(metadataModel.getDocumentType());
        dmc.setEndDate(metadataModel.getEndDate());
        if (metadataModel.getEvent() != null) {
            dmc.getEvent().addAll(mapEvents(metadataModel.getEvent()));
        }
        dmc.setGps(metadataModel.getGps());
        dmc.setOriginatingAgency(metadataModel.getOriginatingAgency());

        if (metadataModel.getFilePlanPosition() != null && !metadataModel.getFilePlanPosition().isEmpty()) {
            dmc.getFilePlanPosition().addAll(metadataModel.getFilePlanPosition());
        }

        if (metadataModel.getSystemId() != null && !metadataModel.getSystemId().isEmpty()) {
            dmc.getSystemId().addAll(metadataModel.getSystemId());
        }

        if (metadataModel.getOriginatingSystemId() != null && !metadataModel.getOriginatingSystemId().isEmpty()) {
            dmc.getOriginatingSystemId().addAll(metadataModel.getOriginatingSystemId());
        }

        if (
            metadataModel.getArchivalAgencyArchiveUnitIdentifier() != null &&
            !metadataModel.getArchivalAgencyArchiveUnitIdentifier().isEmpty()
        ) {
            dmc.getArchivalAgencyArchiveUnitIdentifier().addAll(metadataModel.getArchivalAgencyArchiveUnitIdentifier());
        }

        if (
            metadataModel.getOriginatingAgencyArchiveUnitIdentifier() != null &&
            !metadataModel.getOriginatingAgencyArchiveUnitIdentifier().isEmpty()
        ) {
            dmc
                .getOriginatingAgencyArchiveUnitIdentifier()
                .addAll(metadataModel.getOriginatingAgencyArchiveUnitIdentifier());
        }

        if (
            metadataModel.getTransferringAgencyArchiveUnitIdentifier() != null &&
            !metadataModel.getTransferringAgencyArchiveUnitIdentifier().isEmpty()
        ) {
            dmc
                .getTransferringAgencyArchiveUnitIdentifier()
                .addAll(metadataModel.getTransferringAgencyArchiveUnitIdentifier());
        }

        if (metadataModel.getLanguage() != null && !metadataModel.getLanguage().isEmpty()) {
            dmc.getLanguage().addAll(metadataModel.getLanguage());
        }

        if (metadataModel.getAuthorizedAgent() != null && !metadataModel.getAuthorizedAgent().isEmpty()) {
            dmc.getAuthorizedAgent().addAll(metadataModel.getAuthorizedAgent());
        }

        // seda 2.2 fields
        if (CollectionUtils.isNotEmpty(metadataModel.getAgent())) {
            dmc.getAgent().addAll(metadataModel.getAgent());
        }

        if (CollectionUtils.isNotEmpty(metadataModel.getTextContent())) {
            dmc.getTextContent().addAll(metadataModel.getTextContent());
        }

        dmc.setOriginatingSystemIdReplyTo(metadataModel.getOriginatingSystemIdReplyTo());
        dmc.setDateLitteral(metadataModel.getDateLitteral());

        if (metadataModel.getSignature() != null && !metadataModel.getSignature().isEmpty()) {
            dmc.getSignature().addAll(mapSignatures(metadataModel.getSignature()));
        }

        if (metadataModel.getRecipient() != null && !metadataModel.getRecipient().isEmpty()) {
            dmc.getRecipient().addAll(metadataModel.getRecipient());
        }

        if (metadataModel.getKeyword() != null && !metadataModel.getKeyword().isEmpty()) {
            dmc.getKeyword().addAll(metadataModel.getKeyword());
        }

        dmc.setReceivedDate(metadataModel.getReceivedDate());
        dmc.setRegisteredDate(metadataModel.getRegisteredDate());
        dmc.setRelatedObjectReference(metadataModel.getRelatedObjectReference());
        dmc.setRegisteredDate(metadataModel.getRegisteredDate());
        dmc.setSentDate(metadataModel.getSentDate());
        dmc.setSource(metadataModel.getSource());
        dmc.setStartDate(metadataModel.getStartDate());
        dmc.setStatus(metadataModel.getStatus());
        dmc.setSubmissionAgency(metadataModel.getSubmissionAgency());

        if (metadataModel.getTag() != null) {
            dmc.getTag().addAll(metadataModel.getTag());
        }

        if (metadataModel.getTitle_() != null) {
            dmc.getTitle().addAll(metadataModel.getTitle_().getTextTypes());
        }

        TextType title = new TextType();
        title.setValue(metadataModel.getTitle());
        dmc.getTitle().add(title);

        dmc.setTransactedDate(metadataModel.getTransactedDate());
        dmc.setType(metadataModel.getType());
        dmc.setVersion(metadataModel.getVersion());
        if (metadataModel.getWriter() != null) {
            dmc.getWriter().addAll(metadataModel.getWriter());
        }
        if (metadataModel.getTransmitter() != null) {
            dmc.getTransmitter().addAll(metadataModel.getTransmitter());
        }
        if (metadataModel.getSender() != null) {
            dmc.getSender().addAll(metadataModel.getSender());
        }

        fillHistory(historyListModel, dmc.getHistory());

        return dmc;
    }

    private List<SignatureType> mapSignatures(List<SignatureTypeModel> signatures) {
        if (signatures == null) {
            return null;
        }
        return signatures.stream().map(this::mapSignature).collect(Collectors.toList());
    }

    private SignatureType mapSignature(SignatureTypeModel signatureType) {
        SignatureType result = new SignatureType();
        if (signatureType.getSigner() != null) {
            result.getSigner().addAll(signatureType.getSigner());
        }
        result.setValidator(signatureType.getValidator());
        result.setReferencedObject(mapReferencedObject(signatureType.getReferencedObject()));
        // Not supported in R11
        result.setMasterdata(signatureType.getMasterdata());
        return result;
    }

    private ReferencedObjectType mapReferencedObject(ReferencedObjectTypeModel referencedObject) {
        if (referencedObject == null) {
            return null;
        }
        ReferencedObjectType result = new ReferencedObjectType();
        result.setSignedObjectId(referencedObject.getSignedObjectId());
        result.setSignedObjectDigest(mapSignedObjectDigest(referencedObject.getSignedObjectDigest()));
        return result;
    }

    private MessageDigestBinaryObjectType mapSignedObjectDigest(SignedObjectDigestModel signedMessageDigest) {
        if (signedMessageDigest == null) {
            return null;
        }
        MessageDigestBinaryObjectType result = new MessageDigestBinaryObjectType();
        result.setAlgorithm(signedMessageDigest.getAlgorithm());
        result.setValue(signedMessageDigest.getValue());
        return result;
    }

    private List<EventType> mapEvents(List<EventTypeModel> eventTypes) {
        return eventTypes.stream().map(this::mapEvent).collect(Collectors.toList());
    }

    private EventType mapEvent(EventTypeModel event) {
        EventType eventType = new EventType();
        eventType.setEventDateTime(event.getEventDateTime());
        eventType.setEventDetail(event.getEventDetail());
        eventType.setEventDetailData(event.getEventDetailData());
        eventType.setEventIdentifier(event.getEventIdentifier());
        eventType.setEventType(event.getEventType());
        eventType.setEventTypeCode(event.getEventTypeCode());
        eventType.setOutcome(event.getOutcome());
        eventType.setOutcomeDetail(event.getOutcomeDetail());
        eventType.setOutcomeDetailMessage(event.getOutcomeDetailMessage());
        if (Objects.nonNull(event.getLinkingAgentIdentifier())) {
            eventType
                .getLinkingAgentIdentifier()
                .addAll(
                    event
                        .getLinkingAgentIdentifier()
                        .stream()
                        .map(this::mapLinkingAgentIdentifier)
                        .collect(Collectors.toList())
                );
        }
        return eventType;
    }

    private LinkingAgentIdentifierType mapLinkingAgentIdentifier(
        LinkingAgentIdentifierTypeModel linkingAgentIdentifierTypeModel
    ) {
        if (linkingAgentIdentifierTypeModel == null) {
            return null;
        }
        LinkingAgentIdentifierType linkingAgentIdentifierType = new LinkingAgentIdentifierType();
        linkingAgentIdentifierType.setLinkingAgentIdentifierType(
            linkingAgentIdentifierTypeModel.getLinkingAgentIdentifierType()
        );
        linkingAgentIdentifierType.setLinkingAgentIdentifierValue(
            linkingAgentIdentifierTypeModel.getLinkingAgentIdentifierValue()
        );
        linkingAgentIdentifierType.setLinkingAgentRole(linkingAgentIdentifierTypeModel.getLinkingAgentRole());
        return linkingAgentIdentifierType;
    }

    private void fillHistory(
        List<ArchiveUnitHistoryModel> archiveUnitHistoryModel,
        List<ManagementHistoryType> managementHistoryType
    ) throws DatatypeConfigurationException {
        for (ArchiveUnitHistoryModel historyModel : archiveUnitHistoryModel) {
            ManagementHistoryType historyType = new ManagementHistoryType();
            historyType.setData(new ManagementHistoryDataType());
            historyType.getData().setVersion(historyModel.getData().getVersion());
            historyType.getData().setManagement(managementMapper.map(historyModel.getData().getManagement()));
            Optional<XMLGregorianCalendar> updateDate = stringToXMLGregorianCalendar(historyModel.getUpdateDate());
            if (updateDate.isPresent()) {
                historyType.setUpdateDate(updateDate.get());
            }

            managementHistoryType.add(historyType);
        }
    }

    private Optional<XMLGregorianCalendar> stringToXMLGregorianCalendar(String date)
        throws DatatypeConfigurationException {
        if (ParametersChecker.isNotEmpty(date)) {
            return Optional.of(newInstance().newXMLGregorianCalendar(date));
        } else {
            return Optional.empty();
        }
    }
}
