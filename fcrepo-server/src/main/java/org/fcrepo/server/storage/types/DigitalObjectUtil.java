/* The contents of this file are subject to the license and copyright terms
 * detailed in the license directory at the root of the source tree (also 
 * available online at http://fedora-commons.org/license/).
 */
package org.fcrepo.server.storage.types;

import java.util.Date;
import java.util.Iterator;

import org.fcrepo.common.Constants;
import org.fcrepo.server.errors.GeneralException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DigitalObject utility methods.
 *
 * @author Chris Wilper
 */
public abstract class DigitalObjectUtil
        implements Constants {
    
    private static final Logger logger = LoggerFactory
            .getLogger(DigitalObjectUtil.class);
   
    /**
     * Upgrades a legacy (pre-Fedora-3.0) object by setting the correct MIME
     * type and Format URI for all "reserved" datastreams.
     * 
     * @param obj the object to update.
     */
    @SuppressWarnings("deprecation")
    public static void updateLegacyDatastreams(DigitalObject obj) {
        final String xml = "text/xml";
        final String rdf = "application/rdf+xml";
        updateLegacyDatastream(obj, "DC", xml, OAI_DC2_0.uri);
        updateLegacyDatastream(obj, "RELS-EXT", rdf, RELS_EXT1_0.uri);
        updateLegacyDatastream(obj, "RELS-INT", rdf, RELS_INT1_0.uri);
        updateLegacyDatastream(obj, "POLICY", xml, XACML_POLICY1_0.uri);
        String fType = obj.getExtProperty(RDF.TYPE.uri);
        if (MODEL.BDEF_OBJECT.looselyMatches(fType, false)) {
            updateLegacyDatastream(obj,
                                   "METHODMAP",
                                   xml,
                                   SDEF_METHOD_MAP1_0.uri);
        } else if (MODEL.BMECH_OBJECT.looselyMatches(fType, false)) {
            updateLegacyDatastream(obj,
                                   "METHODMAP",
                                   xml,
                                   SDEP_METHOD_MAP1_1.uri);
            updateLegacyDatastream(obj,
                                   "DSINPUTSPEC",
                                   xml,
                                   DS_INPUT_SPEC1_1.uri);
            updateLegacyDatastream(obj,
                                   "WSDL",
                                   xml,
                                   WSDL.uri);
        }
    }
    
    private static void updateLegacyDatastream(DigitalObject obj,
                                               String dsId,
                                               String mimeType,
                                               String formatURI) {
        for (Datastream ds: obj.datastreams(dsId)) {
            ds.DSMIME = mimeType;
            ds.DSFormatURI = formatURI;
        }
    }
    
    public static void setIngestDefaults(DigitalObject obj, Date nowUTC)
            throws GeneralException {
        // SET OBJECT PROPERTIES:
        logger.debug("Setting object/component states and create dates if unset");
        // set object state to "A" (Active) if not already set
        if (obj.getState() == null || obj.getState().isEmpty()) {
            obj.setState("A");
        }
        // set object create date to UTC if not already set
        if (obj.getCreateDate() == null) {
            obj.setCreateDate(nowUTC);
        }
        // set object last modified date to UTC
        obj.setLastModDate(nowUTC);

        // SET DATASTREAM PROPERTIES...
        Iterator<String> dsIter = obj.datastreamIdIterator();
        while (dsIter.hasNext()) {
            for (Datastream ds : obj.datastreams(dsIter.next())) {
                // Set create date to UTC if not already set
                if (ds.DSCreateDT == null) {
                    ds.DSCreateDT = nowUTC;
                }
                // Set state to "A" (Active) if not already set
                if (ds.DSState == null || ds.DSState.isEmpty()) {
                    ds.DSState = "A";
                }
                ds.DSChecksumType =
                        Datastream
                                .validateChecksumType(ds.DSChecksumType);
            }
        }
    }
}
