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
package fr.gouv.vitam.storage.engine.server.rest;

import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.serverv2.application.AdminApplication;
import fr.gouv.vitam.security.internal.filter.AdminRequestIdFilter;
import fr.gouv.vitam.security.internal.filter.BasicAuthenticationFilter;
import fr.gouv.vitam.storage.engine.common.exception.StorageTechnicalException;
import fr.gouv.vitam.storage.engine.server.distribution.StorageDistribution;
import fr.gouv.vitam.storage.engine.server.distribution.impl.StorageDistributionImpl;
import fr.gouv.vitam.storage.engine.server.offerdiff.OfferDiffService;
import fr.gouv.vitam.storage.engine.server.storagelog.StorageLog;
import fr.gouv.vitam.storage.engine.server.storagelog.StorageLogFactory;

import javax.servlet.ServletConfig;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static fr.gouv.vitam.common.serverv2.application.ApplicationParameter.CONFIGURATION_FILE_APPLICATION;

/**
 * Admin application.
 */
public class AdminStorageApplication extends Application {

    private final AdminApplication adminApplication;

    private Set<Object> singletons;

    /**
     * Constructor.
     *
     * @param servletConfig
     */
    public AdminStorageApplication(@Context ServletConfig servletConfig) {

        String configurationFile = servletConfig.getInitParameter(CONFIGURATION_FILE_APPLICATION);

        try (final InputStream yamlIS = PropertiesUtils.getConfigAsStream(configurationFile)) {

            final StorageConfiguration storageConfiguration =
                PropertiesUtils.readYaml(yamlIS, StorageConfiguration.class);

            adminApplication = new AdminApplication();

            singletons = new HashSet<>();
            singletons.addAll(adminApplication.getSingletons());

            final StorageLog storageLogService = StorageLogFactory.getInstance(VitamConfiguration.getTenants(),
                Paths.get(storageConfiguration.getLoggingDirectory()));

            final StorageDistribution distribution =
                new StorageDistributionImpl(storageConfiguration, storageLogService);

            singletons.add(new AdminOfferSyncResource(distribution, storageConfiguration));
            singletons.add(new AdminOfferDiffResource(new OfferDiffService(distribution)));
            singletons.add(new BasicAuthenticationFilter(storageConfiguration));
            singletons.add(new AdminRequestIdFilter());

        } catch (IOException | StorageTechnicalException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}
