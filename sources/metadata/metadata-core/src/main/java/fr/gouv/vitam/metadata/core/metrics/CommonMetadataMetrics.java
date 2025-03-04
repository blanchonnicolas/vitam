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

package fr.gouv.vitam.metadata.core.metrics;

import fr.gouv.vitam.common.metrics.VitamMetricsNames;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

public class CommonMetadataMetrics {

    /**
     * Compute metadata stream document duration.
     */
    public static final Histogram UNIT_SCROLL_DURATION_HISTOGRAM = Histogram.build()
        .name(VitamMetricsNames.VITAM_METADATA_UNIT_SCROLL_DURATION)
        .labelNames("requestId")
        .help("Vitam metadata stream histogram duration metric")
        .register();

    /**
     * Count metadata stream documents
     */
    public static final Counter UNIT_SCROLL_COUNTER = Counter.build()
        .name(VitamMetricsNames.VITAM_METADATA_UNIT_SCROLL_TOTAL)
        .labelNames("requestId")
        .help("Vitam metadata stream document counter")
        .register();

    /**
     * Compute metadata log shipping duration.
     * This will count number of events and sum durations
     * This will only count effective log shipping and do not count the number off calls ended with (log shipping already running)
     *
     * To count all log shipping events, use
     */
    public static final Histogram LOG_SHIPPING_DURATION = Histogram.build()
        .name(VitamMetricsNames.VITAM_METADATA_LOG_SHIPPING_DURATION)
        .labelNames("collection")
        .help("Vitam metadata effective log shipping histogram duration metric")
        .register();

    /**
     * Count all log shipping events
     * Even when the response is log shipping already running
     */
    public static final Counter LOG_SHIPPING_COUNTER = Counter.build()
        .name(VitamMetricsNames.VITAM_METADATA_LOG_SHIPPING_TOTAL)
        .help("Vitam metadata log shipping events counter for all events. Even for those with response already running")
        .register();
}
