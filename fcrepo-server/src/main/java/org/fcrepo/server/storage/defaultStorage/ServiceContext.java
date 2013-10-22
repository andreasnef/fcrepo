package org.fcrepo.server.storage.defaultStorage;

public class ServiceContext {

    public final String cModel;

    public final String sDef;

    private static final String VAL_TEMPLATE = "(%1$s,%2$s)";
    /* Internal string value for calculating hash code, equality */
    private final String _val;

    private ServiceContext(String cModelPid, String sDefPid) {
        cModel = cModelPid;
        sDef = sDefPid;

        _val = String.format(VAL_TEMPLATE, cModelPid, sDefPid);
    }

    public static ServiceContext getInstance(String cModel, String sDef) {
        return new ServiceContext(cModel, sDef);
    }

    @Override
    public String toString() {
        return _val;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof ServiceContext)) {
            return false;
        }
        return _val.equals(((ServiceContext) o)._val);
    }

    @Override
    public int hashCode() {
        return _val.hashCode();
    }
}