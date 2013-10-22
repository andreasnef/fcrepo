package org.fcrepo.server.storage.defaultStorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.fcrepo.server.storage.DefaultDOManager;
import org.fcrepo.server.storage.types.DigitalObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * A runtime cache of SDep PIDs mapped to the cModels and SDefs
 * they support. Manages the backing db that persists these
 * relationships as well. This class is not synchronized, and
 * the client is responsible for locking to prevent collisions.
 * @author armintor@gmail.com
 * @author Chris Wilper
 * @version $Id$
 *
 */
public class ModelDeploymentMap {

    private static final Logger logger = LoggerFactory
            .getLogger(DefaultDOManager.class);
    
    public static final String CMODEL_QUERY =
            "SELECT cModel, sDef, sDep, mDate " +
            " FROM modelDeploymentMap, doFields " +
            " WHERE doFields.pid = modelDeploymentMap.sDep";

    public static String DELETE_DEPLOYMENT =
            "DELETE FROM modelDeploymentMap "
                    + "WHERE cModel = ? AND sDef =? AND sDep = ?";
    
    public static String INSERT_DEPLOYMENT =
            "INSERT INTO modelDeploymentMap (cModel, sDef, sDep) VALUES (?, ?, ?)";

    private final Map<ServiceContext, Map<String, Long>> map =
            new ConcurrentHashMap<ServiceContext, Map<String, Long>>();
    
    public void initialize(Connection c) throws SQLException {
        PreparedStatement s = null;
        ResultSet r = null;
        try {
            logger.debug("Initializing content model deployment map");
            s = c.prepareStatement(CMODEL_QUERY, ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_READ_ONLY);
            ResultSet results = s.executeQuery();

            while (results.next()) {
                String cModel = results.getString(1);
                String sDef = results.getString(2);
                String sDep = results.getString(3);
                long lastMod = results.getLong(4);

                update(ServiceContext.getInstance(
                        cModel, sDef), sDep, lastMod);
            }

        } finally {
            if (r != null) {
                r.close();
            }
            if (s != null) {
                s.close();
            }
        }
    }

    public void putDeployment(ServiceContext context, DigitalObject sDep,
            Connection c) throws SQLException {

        PreparedStatement s = c.prepareStatement(INSERT_DEPLOYMENT);

        try {
            s.setString(1, context.cModel);
            s.setString(2, context.sDef);
            s.setString(3, sDep.getPid());
            s.executeUpdate();
        } finally {
            if (s != null) {
                s.close();
            }
        }

        updateDeployment(context, sDep);

    }

    public String updateDeployment(
            ServiceContext cxt, DigitalObject sDep) {

        update(cxt, sDep.getPid(), sDep
                .getLastModDate().getTime());

        return getDeployment(cxt);

    }
    
    public void update(ServiceContext cxt, String key, long val) {
        if (!map.containsKey(cxt)) {
            map.put(cxt, new HashMap<String, Long>());
        }

        map.get(cxt).put(key, val);
    }

    /** Removes a deployment from a particular (cModel, sDef) context */
    public void removeDeployment(ServiceContext context, DigitalObject sDep,
            Connection c) throws SQLException {
        PreparedStatement s = c.prepareStatement(DELETE_DEPLOYMENT);
        s.setString(1, context.cModel);
        s.setString(2, context.sDef);
        s.setString(3, sDep.getPid());

        try {
            s.executeUpdate();
        } finally {
            if (s != null) {
                s.close();
            }
        }
        removeDeployment(context, sDep.getPid());

    }

    private String removeDeployment(ServiceContext cxt, String sDep) {

        Map<String, Long> deployments = map.get(cxt);

        if (deployments != null) {
            deployments.remove(sDep);
        }

        return getDeployment(cxt);
    }

    /** Return the OLDEST deployment for a given (cModel, sDef) context */
    public String getDeployment(ServiceContext cxt) {

        if (map.containsKey(cxt)) {
            String sDep = null;
            int count = 0;
            long first = -1;
            for (Map.Entry<String, Long> dep : map.get(cxt).entrySet()) {
                if (dep.getValue() < first || first < 0) {
                    first = dep.getValue();
                    sDep = dep.getKey();
                    count++;
                }
            }

            if (count > 1) {
                logger.info("More than one service deployment specified for sDef {}" +
                        " in model " +
                        "{}.  Using the one with the EARLIEST modification date.",
                        cxt.sDef, cxt.cModel);
            }
            return sDep;
        } else {
            return null;
        }
    }

    /**
     * Return all the (cModel, sDef) contexts a serviceDeployment deploys
     * for
     */
    public Set<ServiceContext> getContextFor(String sDep) {
        Set<ServiceContext> cxt = new HashSet<ServiceContext>();

        for (Entry<ServiceContext, Map<String, Long>> dep : map.entrySet()) {
            if (dep.getValue().keySet().contains(sDep)) {
                cxt.add(dep.getKey());
            }
        }
        return cxt;
    }
    
    public void replaceContexts(DigitalObject obj,
            Set<ServiceContext> newContexts, Connection c)
    throws SQLException {
        String sDep = obj.getPid();
        /* Read in the old deployment map from the cache */
        Set<ServiceContext> oldContexts =
                getContextFor(sDep);

        /* Remove any obsolete deployments from the registry/cache */
        for (ServiceContext o : oldContexts) {
            if (!newContexts.contains(o)) {
                removeDeployment(o, obj, c);
            }
        }

        /* Add any new deployments from the registry/cache */
        for (ServiceContext n : newContexts) {
            if (!oldContexts.contains(n)) {
                putDeployment(n, obj, c);
            } else {
                updateDeployment(n, obj);
            }
        }
    }
}