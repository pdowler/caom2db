
package ca.nrc.cadc.caom2.persistence;


import ca.nrc.cadc.caom2.AbstractCaomEntity;
import ca.nrc.cadc.caom2.Algorithm;
import ca.nrc.cadc.caom2.Artifact;
import ca.nrc.cadc.caom2.CalibrationLevel;
import ca.nrc.cadc.caom2.CaomEntity;
import ca.nrc.cadc.caom2.Chunk;
import ca.nrc.cadc.caom2.CompositeObservation;
import ca.nrc.cadc.caom2.DataProductType;
import ca.nrc.cadc.caom2.DeletedEntity;
import ca.nrc.cadc.caom2.DeletedObservation;
import ca.nrc.cadc.caom2.DeletedObservationMetaReadAccess;
import ca.nrc.cadc.caom2.DeletedPlaneDataReadAccess;
import ca.nrc.cadc.caom2.DeletedPlaneMetaReadAccess;
import ca.nrc.cadc.caom2.Energy;
import ca.nrc.cadc.caom2.EnergyTransition;
import ca.nrc.cadc.caom2.Environment;
import ca.nrc.cadc.caom2.Instrument;
import ca.nrc.cadc.caom2.Metrics;
import ca.nrc.cadc.caom2.Observation;
import ca.nrc.cadc.caom2.ObservationIntentType;
import ca.nrc.cadc.caom2.ObservationURI;
import ca.nrc.cadc.caom2.Part;
import ca.nrc.cadc.caom2.Plane;
import ca.nrc.cadc.caom2.Polarization;
import ca.nrc.cadc.caom2.Position;
import ca.nrc.cadc.caom2.ProductType;
import ca.nrc.cadc.caom2.Proposal;
import ca.nrc.cadc.caom2.Provenance;
import ca.nrc.cadc.caom2.SimpleObservation;
import ca.nrc.cadc.caom2.Target;
import ca.nrc.cadc.caom2.TargetType;
import ca.nrc.cadc.caom2.Telescope;
import ca.nrc.cadc.caom2.Time;
import ca.nrc.cadc.caom2.access.ObservationMetaReadAccess;
import ca.nrc.cadc.caom2.access.PlaneDataReadAccess;
import ca.nrc.cadc.caom2.access.PlaneMetaReadAccess;
import ca.nrc.cadc.caom2.access.ReadAccess;
import ca.nrc.cadc.caom2.persistence.skel.ArtifactSkeleton;
import ca.nrc.cadc.caom2.persistence.skel.ChunkSkeleton;
import ca.nrc.cadc.caom2.persistence.skel.ObservationMetaReadAccessSkeleton;
import ca.nrc.cadc.caom2.persistence.skel.ObservationSkeleton;
import ca.nrc.cadc.caom2.persistence.skel.PartSkeleton;
import ca.nrc.cadc.caom2.persistence.skel.PlaneDataReadAccessSkeleton;
import ca.nrc.cadc.caom2.persistence.skel.PlaneMetaReadAccessSkeleton;
import ca.nrc.cadc.caom2.persistence.skel.PlaneSkeleton;
import ca.nrc.cadc.caom2.persistence.skel.Skeleton;
import ca.nrc.cadc.caom2.types.Point;
import ca.nrc.cadc.caom2.types.Polygon;
import ca.nrc.cadc.caom2.types.PolygonUtil;
import ca.nrc.cadc.caom2.types.PositionUtil;
import ca.nrc.cadc.caom2.wcs.Axis;
import ca.nrc.cadc.caom2.wcs.Coord2D;
import ca.nrc.cadc.caom2.wcs.CoordAxis1D;
import ca.nrc.cadc.caom2.wcs.CoordAxis2D;
import ca.nrc.cadc.caom2.wcs.CoordBounds1D;
import ca.nrc.cadc.caom2.wcs.CoordBounds2D;
import ca.nrc.cadc.caom2.wcs.CoordError;
import ca.nrc.cadc.caom2.wcs.CoordFunction1D;
import ca.nrc.cadc.caom2.wcs.CoordFunction2D;
import ca.nrc.cadc.caom2.wcs.CoordRange1D;
import ca.nrc.cadc.caom2.wcs.CoordRange2D;
import ca.nrc.cadc.caom2.wcs.Dimension2D;
import ca.nrc.cadc.caom2.wcs.ObservableAxis;
import ca.nrc.cadc.caom2.wcs.PolarizationWCS;
import ca.nrc.cadc.caom2.wcs.RefCoord;
import ca.nrc.cadc.caom2.wcs.Slice;
import ca.nrc.cadc.caom2.wcs.SpatialWCS;
import ca.nrc.cadc.caom2.wcs.SpectralWCS;
import ca.nrc.cadc.caom2.wcs.TemporalWCS;
import ca.nrc.cadc.date.DateUtil;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;


/**
 *
 * @author pdowler
 */
public class BaseSQLGenerator implements SQLGenerator
{
    private static Logger log = Logger.getLogger(BaseSQLGenerator.class);
    
    protected static final String BASE_PKG = "ca.nrc.cadc.caom2";
    protected static final Class[] ENTITY_CLASSES =
    {
        Observation.class, Plane.class, Artifact.class, Part.class, Chunk.class,
        ObservationMetaReadAccess.class, PlaneMetaReadAccess.class, PlaneDataReadAccess.class,
        DeletedObservation.class,
        DeletedObservationMetaReadAccess.class, DeletedPlaneMetaReadAccess.class, DeletedPlaneDataReadAccess.class
    };
    protected static final Class[] SKELETON_CLASSES =
    {
        ObservationSkeleton.class, PlaneSkeleton.class, ArtifactSkeleton.class, PartSkeleton.class, ChunkSkeleton.class,
        ObservationMetaReadAccessSkeleton.class, PlaneMetaReadAccessSkeleton.class, PlaneDataReadAccessSkeleton.class
    };

    static final String SIMPLE_TYPE = "S";
    static final String COMPOSITE_TYPE = "C";

    private Calendar UTC_CAL = Calendar.getInstance(DateUtil.UTC);

    protected String database;
    protected String schema;
    protected boolean useIntegerForBoolean = true; // TAP default
    protected boolean persistTransientState = false; // persist computed plane metadata
    protected int numComputedObservationColumns;
    protected int numComputedPlaneColumns;

    // map of Class to the table that stores instances
    protected final Map<Class,String> tableMap = new TreeMap<Class,String>(new ClassComp());

    // map of Class to String[] with all the column names
    protected final Map<Class,String[]> columnMap = new TreeMap<Class,String[]>(new ClassComp());

    // map of Class to standard alias name used in all select queries (w/ joins)
    protected final Map<Class,String> aliasMap = new TreeMap<Class,String>(new ClassComp());

    protected final Map<Class,String> alternateLastModifiedColumn = new TreeMap<Class,String>(new ClassComp());

    protected DateFormat dateFormat = DateUtil.getDateFormat(DateUtil.ISO_DATE_FORMAT, DateUtil.UTC);
    
    private BaseSQLGenerator() { }
    
    public BaseSQLGenerator(String database, String schema, String tablePrefix, boolean persistComputedValues)
    { 
        this.database = database;
        this.schema = schema;
        this.persistTransientState = persistComputedValues;
        
        for (Class c : ENTITY_CLASSES)
        {
            if (tablePrefix != null)
                tableMap.put(c, tablePrefix + c.getSimpleName());
            else
                tableMap.put(c, c.getSimpleName());
            aliasMap.put(c, c.getSimpleName());
        }
        for (Class c : SKELETON_CLASSES)
        {
            String s = c.getSimpleName();
            s = s.replace("Skeleton", "");
            if (tablePrefix != null)
                tableMap.put(c, tablePrefix + s);
            else
                tableMap.put(c, s);
            aliasMap.put(c, c.getSimpleName());
        }
        // IMPORTANT:
        // - the primary key column is LAST in the list of columns so that
        //   insert and update statements have the same argument number and order
        // - the foreign key column is FIRST so the ResultSetExtractor can tell when
        //   it hits a null object
        // - the typeCode column is first so the right subclass of observation can be created
        String[] obsColumns = new String[]
        {
            "typeCode", 
            "collection", "observationID", "algorithm_name", 
            "type", "intent", "sequenceNumber", "metaRelease",
            "proposal_id", "proposal_pi", "proposal_project", "proposal_title", "proposal_keywords",
            "target_name", "target_type", "target_standard",
            "target_redshift", "target_keywords",
            "telescope_name", "telescope_geoLocationX", "telescope_geoLocationY", "telescope_geoLocationZ", "telescope_keywords",
            "instrument_name", "instrument_keywords",
            "environment_seeing", "environment_humidity", "environment_elevation",
            "environment_tau", "environment_wavelengthTau", "environment_ambientTemp",
            "environment_photometric",
            "members",

            "lastModified", "maxLastModified", "stateCode", "obsID"
        };
        if (persistComputedValues)
        {
            String[] computedObsColumns = new String[]
            {
                "observationURI"
            };
            this.numComputedObservationColumns = computedObsColumns.length;
            int n = obsColumns.length + computedObsColumns.length;
            String[] allCols = new String[n];

            // insert the computed columns before the CaomEntity columns and PK (last 4)
            System.arraycopy(obsColumns, 0, allCols, 0, obsColumns.length - 4);
            int num = obsColumns.length - 4;
            System.arraycopy(computedObsColumns, 0, allCols, num, computedObsColumns.length);
            num += computedObsColumns.length;
            System.arraycopy(obsColumns, obsColumns.length - 4, allCols, num, 4);
            obsColumns = allCols;
        }
        columnMap.put(Observation.class, obsColumns);

        String[] planeColumns = new String[]
        {
            "obsID", 
            "productID",
            "metaRelease", "dataRelease", "dataProductType", "calibrationLevel",
            "provenance_name", "provenance_reference", "provenance_version", "provenance_project",
            "provenance_producer", "provenance_runID", "provenance_lastExecuted",
            "provenance_inputs", "provenance_keywords",
            "metrics_sourceNumberDensity", "metrics_background", "metrics_backgroundStddev",
            "metrics_fluxDensityLimit", "metrics_magLimit",

            "lastModified", "maxLastModified", "stateCode", "planeID"
        };

        if (persistComputedValues)
        {
            String[] computedPlaneColumns = new String[]
            {
                "planeURI",
                
                // position
                "position_bounds", "position_bounds_center", "position_bounds_area", "position_bounds_size", "position_bounds_encoded",
                "position_dimension1", "position_dimension2",
                "position_resolution", "position_sampleSize", "position_timeDependent",

                // energy
                "energy_emBand", "energy_bounds_cval1", "energy_bounds_cval2", "energy_bounds_width",
                "energy_freqWidth", "energy_freqSampleSize",
                "energy_dimension", "energy_resolvingPower", "energy_sampleSize",
                "energy_bandpassName", "energy_transition_species", "energy_transition_transition",
                

                // time
                "time_bounds_cval1", "time_bounds_cval2", "time_bounds_width",
                "time_dimension", "time_resolution", "time_sampleSize", "time_exposure",

                // polarization
                "polarization_states", "polarization_dimension"
            };
            this.numComputedPlaneColumns = computedPlaneColumns.length;
            int n = planeColumns.length + computedPlaneColumns.length;
            String[] allCols = new String[n];

            // insert the computed columns before the CaomEntity columns and PK (last 4)
            System.arraycopy(planeColumns, 0, allCols, 0, planeColumns.length - 4);
            int num = planeColumns.length - 4;
            System.arraycopy(computedPlaneColumns, 0, allCols, num, computedPlaneColumns.length);
            num += computedPlaneColumns.length;
            System.arraycopy(planeColumns, planeColumns.length - 4, allCols, num, 4);
            planeColumns = allCols;
        }
        columnMap.put(Plane.class, planeColumns);

        String[] artifactColumns = new String[]
        {
            "planeID", "obsID",
            "metaRelease", 
            "uri", "productType", "contentType", "contentLength", "alternative",
            "lastModified", "maxLastModified", "stateCode", "artifactID"
        };
        columnMap.put(Artifact.class, artifactColumns);

        String[] partColumns = new String[]
        {
            "artifactID", "planeID", "obsID",
            "metaRelease", 
            "name", "productType",
            "lastModified", "maxLastModified", "stateCode", "partID"
        };
        columnMap.put(Part.class, partColumns);

        String[] chunkColumns = new String[]
        {
            "partID", "artifactID", "planeID", "obsID",
            "metaRelease", 
            "productType", "naxis", 
            "positionAxis1", "positionAxis2", "energyAxis", "timeAxis", "polarizationAxis", "observableAxis",
            
            "position_axis_axis1_ctype",
            "position_axis_axis1_cunit",
            "position_axis_axis2_ctype",
            "position_axis_axis2_cunit",
            "position_axis_error1_syser",
            "position_axis_error1_rnder",
            "position_axis_error2_syser",
            "position_axis_error2_rnder",
            "position_axis_range_start_coord1_pix", "position_axis_range_start_coord1_val",
            "position_axis_range_start_coord2_pix", "position_axis_range_start_coord2_val",
            "position_axis_range_end_coord1_pix", "position_axis_range_end_coord1_val",
            "position_axis_range_end_coord2_pix", "position_axis_range_end_coord2_val",
            "position_axis_bounds",
            "position_axis_function_dimension_naxis1", "position_axis_function_dimension_naxis2",
            "position_axis_function_refcoord_coord1_pix", "position_axis_function_refCoord_coord1_val",
            "position_axis_function_refCoord_coord2_pix", "position_axis_function_refCoord_coord2_val",
            "position_axis_function_cd11", "position_axis_function_cd12",
            "position_axis_function_cd21", "position_axis_function_cd22",
            "position_coordsys", "position_equinox", "position_resolution",

            "energy_axis_axis_ctype", "energy_axis_axis_cunit",
            "energy_axis_error_syser", "energy_axis_error_rnder",
            "energy_axis_range_start_pix", "energy_axis_range_start_val",
            "energy_axis_range_end_pix", "energy_axis_range_end_val",
            "energy_axis_bounds",
            "energy_axis_function_naxis",
            "energy_axis_function_refCoord_pix", "energy_axis_function_refCoord_val",
            "energy_axis_function_delta",

            "energy_specsys", "energy_ssysobs", "energy_ssyssrc",
            "energy_restfrq", "energy_restwav",
            "energy_velosys", "energy_zsource", "energy_velang",
            "energy_bandpassName", "energy_resolvingPower",
            "energy_transition_species", "energy_transition_transition",

            "time_axis_axis_ctype", "time_axis_axis_cunit",
            "time_axis_error_syser", "time_axis_error_rnder",
            "time_axis_range_start_pix", "time_axis_range_start_val",
            "time_axis_range_end_pix", "time_axis_range_end_val",
            "time_axis_bounds",
            "time_axis_function_naxis",
            "time_axis_function_refCoord_pix", "time_axis_function_refCoord_val",
            "time_axis_function_delta",
            "time_timesys", "time_trefpos", "time_mjdref",
            "time_exposure", "time_resolution",

            "polarization_axis_axis_ctype", "polarization_axis_axis_cunit",
            "polarization_axis_error_syser", "polarization_axis_error_rnder",
            "polarization_axis_range_start_pix", "polarization_axis_range_start_val",
            "polarization_axis_range_end_pix", "polarization_axis_range_end_val",
            "polarization_axis_bounds",
            "polarization_axis_function_naxis",
            "polarization_axis_function_refCoord_pix", "polarization_axis_function_refCoord_val",
            "polarization_axis_function_delta",

            "observable_dependent_axis_ctype",
            "observable_dependent_axis_cunit",
            "observable_dependent_bin",
            "observable_independent_axis_ctype",
            "observable_independent_axis_cunit",
            "observable_independent_bin",

            "lastModified", "maxLastModified", "stateCode", "chunkID"
        };
        columnMap.put(Chunk.class, chunkColumns);

        String[] metaReadAccessCols = new String[]
        {
            "assetID", "groupID", "lastModified", "stateCode", "readAccessID"
        };
        columnMap.put(ObservationMetaReadAccess.class, metaReadAccessCols);
        columnMap.put(PlaneMetaReadAccess.class, metaReadAccessCols);
        columnMap.put(PlaneDataReadAccess.class, metaReadAccessCols);

        String[] deletedCols = new String[]
        {
            "lastModified", "id"
        };
        columnMap.put(DeletedObservation.class, deletedCols);
        columnMap.put(DeletedObservationMetaReadAccess.class, deletedCols);
        columnMap.put(DeletedPlaneMetaReadAccess.class, deletedCols);
        columnMap.put(DeletedPlaneDataReadAccess.class, deletedCols);

        //columnMap.put(ObservationSkeleton.class, new String[] { "lastModified", "stateCode", "obsID" });
        columnMap.put(ObservationSkeleton.class, new String[] { "maxLastModified", "stateCode", "obsID" });
        columnMap.put(PlaneSkeleton.class, new String[] { "lastModified", "stateCode", "planeID" });
        columnMap.put(ArtifactSkeleton.class, new String[] { "lastModified", "stateCode", "artifactID" });
        columnMap.put(PartSkeleton.class, new String[] { "lastModified", "stateCode", "partID" });
        columnMap.put(ChunkSkeleton.class, new String[] { "lastModified", "stateCode", "chunkID" });
        columnMap.put(ObservationMetaReadAccessSkeleton.class, new String[] { "lastModified", "stateCode", "readAccessID" });
        columnMap.put(PlaneMetaReadAccessSkeleton.class, new String[] { "lastModified", "stateCode", "readAccessID" });
        columnMap.put(PlaneDataReadAccessSkeleton.class, new String[] { "lastModified", "stateCode", "readAccessID" });
    }

    public boolean persistTransientState()
    {
        return persistTransientState;
    }

    public String getCatalog() { return database; }
    
    public String getSchema() { return schema; }
    
    public String getSelectSQL(ObservationURI uri, int depth)
    {
        return getSelectSQL(uri, depth, false); 
    }

    public String getSelectSQL(ObservationURI uri, int depth, boolean skeleton)
    {
        StringBuilder sb = new StringBuilder();
        String alias = getAlias(Observation.class);
        if (skeleton)
            alias = getAlias(ObservationSkeleton.class);
        sb.append("SELECT ");
        sb.append(getObservationSelect(depth, skeleton));
        sb.append(" WHERE ");
        sb.append(alias);
        sb.append(".collection = ");
        sb.append(literal(uri.getCollection()));
        sb.append(" AND ");
        sb.append(alias);
        sb.append(".observationID = ");
        sb.append(literal(uri.getObservationID()));
        String orderBy = getOrderColumns(depth);
        if (skeleton)
            orderBy = getSkeletonOrderColumns(depth);
        if (orderBy != null)
        {
            sb.append(" ORDER BY ");
            sb.append(orderBy);
        }
        return sb.toString();
    }
    public String getSelectSQL(Long id, int depth, boolean skeleton)
    {
        StringBuilder sb = new StringBuilder();
        String alias = getAlias(ObservationSkeleton.class);
        if (skeleton)
            alias = getAlias(ObservationSkeleton.class);
        sb.append("SELECT ");
        sb.append(getObservationSelect(depth, skeleton));
        sb.append(" WHERE ");
        sb.append(alias);
        sb.append(".");
        if (skeleton)
            sb.append(getPrimaryKeyColumn(ObservationSkeleton.class));
        else
            sb.append(getPrimaryKeyColumn(Observation.class));
        sb.append(" = ");
        sb.append(literal(id));
        String orderBy = getOrderColumns(depth);
        if (skeleton)
            orderBy = getSkeletonOrderColumns(depth);
        if (orderBy != null)
        {
            sb.append(" ORDER BY ");
            sb.append(orderBy);
        }
        return sb.toString();
    }

    // select batchSize Observation.maxLastModified, starting at minLastModified and in maxLastModified order
    public String getSelectLastModifiedRangeSQL(Class c, Date minLastModified, Date maxLastModified, Integer batchSize)
    {
        if (!Observation.class.equals(c))
            throw new UnsupportedOperationException("incremental list query for " + c.getSimpleName());

        DateFormat df = DateUtil.getDateFormat(DateUtil.ISO_DATE_FORMAT, DateUtil.UTC);
        String top = getTopConstraint(batchSize);
        String limit = getLimitConstraint(batchSize);

        StringBuilder sb = new StringBuilder();
        String alias = getAlias(Observation.class);
        sb.append("SELECT ");
        if (top != null && top.length() > 0)
        {
            sb.append(top);
            sb.append(" ");
        }
        sb.append(alias).append(".maxLastModified FROM ");
        sb.append(getFrom(c));
        if (minLastModified != null)
        {
            sb.append(" WHERE ");
            sb.append(alias).append(".maxLastModified >= '");
            sb.append(df.format(minLastModified));
            sb.append("'");
        }
        if (maxLastModified != null)
        {
            if (minLastModified == null)
                sb.append(" WHERE ");
            else
                sb.append(" AND ");
            sb.append(alias).append(".maxLastModified <= '");
            sb.append(df.format(maxLastModified));
            sb.append("'");
        }
        sb.append(" ORDER BY ");
        sb.append(alias).append(".maxLastModified");
        if (limit != null && limit.length() > 0)
        {
            sb.append(" ");
            sb.append(limit);
        }
        return sb.toString();
    }

    // select batchSize instances of c, starting at minLastModified and in lastModified order
    public String getSelectSQL(Class c, Date minLastModified, Date maxLastModified, Integer batchSize)
    {
        DateFormat df = DateUtil.getDateFormat(DateUtil.ISO_DATE_FORMAT, DateUtil.UTC);
        String top = getTopConstraint(batchSize);
        String limit = getLimitConstraint(batchSize);

        String lastModifiedColumn = alternateLastModifiedColumn.get(c);
        if (lastModifiedColumn == null)
            lastModifiedColumn = "lastModified";
        
        StringBuilder sb = new StringBuilder();
        String alias = getAlias(c);
        sb.append("SELECT ");
        if (top != null && top.length() > 0)
        {
            sb.append(top);
            sb.append(" ");
        }
        sb.append(getColumns(c));
        sb.append(" FROM ");
        sb.append(getFrom(c));
        if (minLastModified != null)
        {
            sb.append(" WHERE ");
            sb.append(alias).append(".").append(lastModifiedColumn).append(" >= '");
            sb.append(df.format(minLastModified));
            sb.append("'");
        }
        if (maxLastModified != null)
        {
            if (minLastModified == null)
                sb.append(" WHERE ");
            else
                sb.append(" AND ");
            sb.append(alias).append(".").append(lastModifiedColumn).append(" <= '");
            sb.append(df.format(maxLastModified));
            sb.append("'");
        }
        sb.append(" ORDER BY ");
        sb.append(alias).append(".").append(lastModifiedColumn);
        if (limit != null && limit.length() > 0)
        {
            sb.append(" ");
            sb.append(limit);
        }
        return sb.toString();
    }

    // select Observation(s) with maxLastmodified in [minLastModified,maxLastModified]
    public String getSelectSQL(Class c, Date minLastModified, Date maxLastModified)
    {
        if (!Observation.class.equals(c))
            throw new UnsupportedOperationException("incremental list query for " + c.getSimpleName());

        DateFormat df = DateUtil.getDateFormat(DateUtil.ISO_DATE_FORMAT, DateUtil.UTC);

        StringBuilder sb = new StringBuilder();
        String alias = getAlias(Observation.class);
        sb.append("SELECT ");
        sb.append(getObservationSelect(MAX_DEPTH, false));
        boolean and = false;
        if (minLastModified != null)
        {
            sb.append(" WHERE ");
            sb.append(alias).append(".maxLastModified >= '");
            sb.append(df.format(minLastModified));
            sb.append("'");
            and = true;
        }
        if (maxLastModified != null)
        {
            if (and) 
                sb.append(" AND ");
            else
                sb.append(" WHERE ");
            sb.append(alias).append(".maxLastModified <= '");
            sb.append(df.format(maxLastModified));
            sb.append("'");
        }
        String orderBy = getOrderColumns(MAX_DEPTH);
        if (orderBy != null)
        {
            sb.append(" ORDER BY ");
            sb.append(orderBy);
        }
        return sb.toString();
    }

    protected String getTopConstraint(Integer batchSize)
    {
        return null;
    }
    protected String getLimitConstraint(Integer batchSize)
    {
        return null;
    }

    public String getSelectSQL(Class clz, Long id)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        String[] cols = columnMap.get(clz);
        for (int c=0; c<cols.length; c++)
        {
            if (c > 0)
                sb.append(",");
            sb.append(cols[c]);
        }
        sb.append(" FROM ");
        sb.append(getTable(clz));
        sb.append(" WHERE ");
        sb.append(getPrimaryKeyColumn(clz));
        sb.append(" = ");
        sb.append(literal(id));
        return sb.toString();
    }


    public String getDeleteSQL(Class c, Long id, boolean primaryKey)
    {
        if (Observation.class.isAssignableFrom(c))
            c = Observation.class;
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(getTable(c));
        sb.append(" WHERE ");
        if (primaryKey)
            sb.append(getPrimaryKeyColumn(c));
        else
            sb.append(getForeignKeyColumn(c));
        sb.append(" = ");
        sb.append(literal(id));
        return sb.toString();
    }

    public String getPrimaryKeyColumn(Class c)
    {
        if (Observation.class.isAssignableFrom(c))
            c = Observation.class;
        String[] cols = columnMap.get(c);
        return cols[cols.length - 1]; // last column is PK
    }
    public String getForeignKeyColumn(Class c)
    {
        if (Observation.class.isAssignableFrom(c) 
                || ReadAccess.class.isAssignableFrom(c)
                || DeletedEntity.class.isAssignableFrom(c))
            throw new IllegalArgumentException(c.getSimpleName() + " does not have a foreign key");
        String[] cols = columnMap.get(c);
        return cols[0]; // first column is FK
    }

    private String getInsertSQL(Class clz)
    {
        StringBuilder sb = new StringBuilder();
        String[] cols = columnMap.get(clz);

        sb.append("INSERT INTO ");
        sb.append(getTable(clz));
        sb.append(" (");
        for (int c=0; c<cols.length; c++)
        {
            if (c > 0)
                sb.append(",");
            sb.append(cols[c]);
        }
        sb.append(" ) VALUES (");
        for (int c=0; c<cols.length; c++)
        {
            if (c > 0)
                sb.append(",?");
            else
                sb.append("?");
        }
        sb.append(")");

        return sb.toString();
    }
    private String getUpdateSQL(Class clz)
    {
        StringBuilder sb = new StringBuilder();
        String[] cols = columnMap.get(clz);

        sb.append("UPDATE ");
        sb.append(getTable(clz));
        sb.append(" SET ");
        for (int c=0; c<cols.length - 1; c++) // PK is last
        {
            if (c > 0)
                sb.append(",");
            sb.append(cols[c]);
            sb.append(" = ?");
        }
        sb.append(" WHERE ");
        sb.append(getPrimaryKeyColumn(clz));
        sb.append(" = ?");

        return sb.toString();
    }

    public EntityPut getEntityPut(Class<? extends AbstractCaomEntity> c, boolean isUpdate)
    {
        if ( Observation.class.isAssignableFrom(c))
            return new ObservationPut(isUpdate);

        if ( Plane.class.equals(c))
            return new PlanePut(isUpdate);

        if ( Artifact.class.equals(c))
            return new ArtifactPut(isUpdate);

        if ( Part.class.equals(c))
            return new PartPut(isUpdate);

        if ( Chunk.class.equals(c))
            return new ChunkPut(isUpdate);

        if ( ReadAccess.class.isAssignableFrom(c))
            return new ReadAccessPut(isUpdate);

        throw new UnsupportedOperationException();
    }
    
    private class ObservationPut implements EntityPut<Observation>
    {
        boolean update;
        Observation obs;

        ObservationPut(boolean update) { this.update = update; }
        
        public void setValue(Observation obs, List<CaomEntity> unused) { this.obs = obs; }

        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            String sql = null;
            if (update)
                sql = getUpdateSQL(Observation.class);
            else
                sql = getInsertSQL(Observation.class);
            PreparedStatement prep = conn.prepareStatement(sql);
            log.debug(sql);
            loadValues(prep);
            return prep;
        }
        private void loadValues(PreparedStatement ps)
            throws SQLException
        {
            if (obs == null)
                throw new IllegalStateException("null observation");

            StringBuilder sb = null;
            if (log.isDebugEnabled())
                sb = new StringBuilder();

            int col = 1;
            if (obs instanceof CompositeObservation)
                safeSetString(sb, ps, col++, COMPOSITE_TYPE);
            else
                safeSetString(sb, ps, col++,SIMPLE_TYPE);
            safeSetString(sb, ps, col++, obs.getURI().getCollection());
            safeSetString(sb, ps, col++, obs.getURI().getObservationID());
            safeSetString(sb, ps, col++, obs.getAlgorithm().getName());
            safeSetString(sb, ps, col++, obs.type);
            if (obs.intent != null)
                safeSetString(sb, ps, col++, obs.intent.getValue());
            else
                safeSetString(sb, ps, col++, null);
            safeSetInteger(sb, ps, col++, obs.sequenceNumber);

            safeSetDate(sb, ps, col++, Util.truncate(obs.metaRelease), UTC_CAL);
            if (obs.proposal != null)
            {
                safeSetString(sb, ps, col++, obs.proposal.getID());
                safeSetString(sb, ps, col++, obs.proposal.pi);
                safeSetString(sb, ps, col++, obs.proposal.project);
                safeSetString(sb, ps, col++, obs.proposal.title);
                safeSetString(sb, ps, col++, Util.encodeListString(obs.proposal.getKeywords()));
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
            }
            if (obs.target != null)
            {
                safeSetString(sb, ps, col++, obs.target.getName());
                if (obs.target.type != null)
                    safeSetString(sb, ps, col++, obs.target.type.getValue());
                else
                    safeSetString(sb, ps, col++, null);
                safeSetBoolean(sb, ps, col++, obs.target.standard);
                safeSetDouble(sb, ps, col++, obs.target.redshift);
                safeSetString(sb, ps, col++, Util.encodeListString(obs.target.getKeywords()));
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetBoolean(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
            }
            if (obs.telescope != null)
            {
                safeSetString(sb, ps, col++, obs.telescope.getName());
                safeSetDouble(sb, ps, col++, obs.telescope.geoLocationX);
                safeSetDouble(sb, ps, col++, obs.telescope.geoLocationY);
                safeSetDouble(sb, ps, col++, obs.telescope.geoLocationZ);
                safeSetString(sb, ps, col++, Util.encodeListString(obs.telescope.getKeywords()));
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
            }
            if (obs.instrument != null)
            {
                safeSetString(sb, ps, col++, obs.instrument.getName());
                safeSetString(sb, ps, col++, Util.encodeListString(obs.instrument.getKeywords()));
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
            }
            if (obs.environment != null)
            {
                safeSetDouble(sb, ps, col++, obs.environment.seeing);
                safeSetDouble(sb, ps, col++, obs.environment.humidity);
                safeSetDouble(sb, ps, col++, obs.environment.elevation);
                safeSetDouble(sb, ps, col++, obs.environment.tau);
                safeSetDouble(sb, ps, col++, obs.environment.wavelengthTau);
                safeSetDouble(sb, ps, col++, obs.environment.ambientTemp);
                safeSetBoolean(sb, ps, col++, obs.environment.photometric);
            }
            else
            {
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetBoolean(sb, ps, col++, null);
            }

            if (obs instanceof CompositeObservation)
            {
                CompositeObservation co = (CompositeObservation) obs;
                safeSetString(sb, ps, col++, Util.encodeObservationURIs(co.getMembers()));
            }
            else
                safeSetString(sb, ps, col++, null);

            if (persistTransientState)
            {
                safeSetString(sb, ps, col++, obs.getURI().getURI().toString());
            }
            
            safeSetDate(sb, ps, col++, obs.getLastModified(), UTC_CAL);
            safeSetDate(sb, ps, col++, obs.getMaxLastModified(), UTC_CAL);
            safeSetInteger(sb, ps, col++, obs.getStateCode());
            safeSetLong(sb, ps, col++, obs.getID());
            if (sb != null)
                log.debug(sb.toString());
        }
    }

    private class PlanePut implements EntityPut<Plane>
    {
        private boolean update;
        private Plane plane;
        private List<CaomEntity> parents;
        PlanePut(boolean update) { this.update = update; }

        public void setValue(Plane plane, List<CaomEntity> parents)
        {
            this.plane = plane;
            this.parents = parents;
        }

        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            String sql = null;
            if (update)
                sql = getUpdateSQL(Plane.class);
            else
                sql = getInsertSQL(Plane.class);
            PreparedStatement prep = conn.prepareStatement(sql);
            log.debug(sql);
            loadValues(prep);
            return prep;
        }
        private void loadValues(PreparedStatement ps)
            throws SQLException
        {
            if (plane == null)
                throw new IllegalStateException("null observation");
            Observation obs = (Observation) parents.get(0);
            
            StringBuilder sb = null;
            if (log.isDebugEnabled())
                sb = new StringBuilder();

            int col = 1;
            safeSetLong(sb, ps, col++, obs.getID()); // obsID
            
            safeSetString(sb, ps, col++, plane.getProductID());
            safeSetDate(sb, ps, col++, Util.truncate(plane.metaRelease), UTC_CAL);
            safeSetDate(sb, ps, col++, Util.truncate(plane.dataRelease), UTC_CAL);
            if (plane.dataProductType != null)
                safeSetString(sb, ps, col++, plane.dataProductType.getValue());
            else
                safeSetString(sb, ps, col++, null);
            if (plane.calibrationLevel != null)
                safeSetInteger(sb, ps, col++, plane.calibrationLevel.getValue());
            else
                safeSetInteger(sb, ps, col++, null);
            
            if (plane.provenance != null)
            {
                safeSetString(sb, ps, col++, plane.provenance.getName());
                if (plane.provenance.reference != null)
                    safeSetString(sb, ps, col++, plane.provenance.reference.toASCIIString());
                else
                    safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, plane.provenance.version);
                safeSetString(sb, ps, col++, plane.provenance.project);
                safeSetString(sb, ps, col++, plane.provenance.producer);
                safeSetString(sb, ps, col++, plane.provenance.runID);
                safeSetDate(sb, ps, col++, Util.truncate(plane.provenance.lastExecuted), UTC_CAL);
                safeSetString(sb, ps, col++, Util.encodePlaneURIs(plane.provenance.getInputs()));
                safeSetString(sb, ps, col++, Util.encodeListString(plane.provenance.getKeywords()));
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetDate(sb, ps, col++, null, UTC_CAL);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
            }

            if (plane.metrics != null)
            {
                safeSetDouble(sb, ps, col++, plane.metrics.sourceNumberDensity);
                safeSetDouble(sb, ps, col++, plane.metrics.background);
                safeSetDouble(sb, ps, col++, plane.metrics.backgroundStddev);
                safeSetDouble(sb, ps, col++, plane.metrics.fluxDensityLimit);
                safeSetDouble(sb, ps, col++, plane.metrics.magLimit);
            }
            else
            {
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
            }

            if (persistTransientState)
            {
                // planeURI
                safeSetString(sb, ps, col++, plane.getURI(obs.getURI()).getURI().toASCIIString());

                //position
                Position pos = plane.getPosition();
                if (pos.bounds != null)
                {
                    Polygon poly = PolygonUtil.toPolygon(pos.bounds);
                    safeSetPolygon(sb, ps, col++, poly);
                    safeSetPoint(sb, ps, col++, pos.bounds.getCenter());
                    safeSetDouble(sb, ps, col++, pos.bounds.getArea());
                    safeSetDouble(sb, ps, col++, pos.bounds.getSize());
                    safeSetBinary(sb, ps, col++, PositionUtil.encode(pos.bounds));
                }
                else
                {
                    safeSetPolygon(sb, ps, col++, null);
                    safeSetPoint(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                    safeSetBinary(sb, ps, col++, null);
                }

                if (pos.dimension != null)
                {
                    safeSetLong(sb, ps, col++, pos.dimension.naxis1);
                    safeSetLong(sb, ps, col++, pos.dimension.naxis2);
                }
                else
                {
                    safeSetLong(sb, ps, col++, null);
                    safeSetLong(sb, ps, col++, null);
                }
                safeSetDouble(sb, ps, col++, pos.resolution);
                safeSetDouble(sb, ps, col++, pos.sampleSize);
                safeSetBoolean(sb, ps, col++, pos.timeDependent);

                //energy
                Energy nrg = plane.getEnergy();
                if (nrg.emBand != null)
                    safeSetString(sb, ps, col++, nrg.emBand.getValue());
                else
                    safeSetString(sb, ps, col++, null);
                if (nrg.bounds != null)
                {
                    safeSetDouble(sb, ps, col++, nrg.bounds.getLower());
                    safeSetDouble(sb, ps, col++, nrg.bounds.getUpper());
                    safeSetDouble(sb, ps, col++, nrg.bounds.getWidth());
                }
                else
                {
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                }
                safeSetDouble(sb, ps, col++, nrg.getFreqWidth());
                safeSetDouble(sb, ps, col++, nrg.getFreqSampleSize());
                safeSetLong(sb, ps, col++, nrg.dimension);
                safeSetDouble(sb, ps, col++, nrg.resolvingPower);
                safeSetDouble(sb, ps, col++, nrg.sampleSize);
                safeSetString(sb, ps, col++, nrg.bandpassName);
                if (nrg.transition != null)
                {
                    safeSetString(sb, ps, col++, nrg.transition.getSpecies());
                    safeSetString(sb, ps, col++, nrg.transition.getTransition());
                }
                else
                {
                    safeSetString(sb, ps, col++, null);
                    safeSetString(sb, ps, col++, null);
                }

                //time
                Time tim = plane.getTime();
                if (tim.bounds != null)
                {
                    safeSetDouble(sb, ps, col++, tim.bounds.getLower());
                    safeSetDouble(sb, ps, col++, tim.bounds.getUpper());
                    safeSetDouble(sb, ps, col++, tim.bounds.getWidth());
                }
                else
                {
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                }
                safeSetLong(sb, ps, col++, tim.dimension);
                safeSetDouble(sb, ps, col++, tim.resolution);
                safeSetDouble(sb, ps, col++, tim.sampleSize);
                safeSetDouble(sb, ps, col++, tim.exposure);

                //polarization
                Polarization pol = plane.getPolarization();
                safeSetString(sb, ps, col++, Util.encodeStates(pol.states));
                safeSetInteger(sb, ps, col++, pol.dimension);
            }

            safeSetDate(sb, ps, col++, plane.getLastModified(), UTC_CAL);
            safeSetDate(sb, ps, col++, plane.getMaxLastModified(), UTC_CAL);
            safeSetInteger(sb, ps, col++, plane.getStateCode());
            safeSetLong(sb, ps, col++, plane.getID());

            if (sb != null)
                log.debug(sb.toString());
        }
    }

    private class ArtifactPut implements EntityPut<Artifact>
    {
        private boolean update;
        private Artifact artifact;
        private List<CaomEntity> parents;

        ArtifactPut(boolean update) { this.update = update; }

        public void setValue(Artifact a, List<CaomEntity> parents)
        {
            this.artifact = a;
            this.parents = parents;
        }

        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            String sql = null;
            if (update)
                sql = getUpdateSQL(Artifact.class);
            else
                sql = getInsertSQL(Artifact.class);
            PreparedStatement prep = conn.prepareStatement(sql);
            log.debug(sql);
            loadValues(prep);
            return prep;
        }
        private void loadValues(PreparedStatement ps)
            throws SQLException
        {
            if (artifact == null)
                throw new IllegalStateException("null artifact");
            // stack
            Observation obs = (Observation) parents.get(1);
            Plane plane = (Plane) parents.get(0);

            StringBuilder sb = null;
            if (log.isDebugEnabled())
                sb = new StringBuilder();

            int col = 1;
            safeSetLong(sb, ps, col++, plane.getID());
            safeSetLong(sb, ps, col++, obs.getID());
            safeSetDate(sb, ps, col++, Util.truncate(plane.metaRelease), UTC_CAL);
            
            safeSetString(sb, ps, col++, artifact.getURI().toASCIIString());
            if (artifact.productType != null)
                safeSetString(sb, ps, col++, artifact.productType.getValue());
            else
                safeSetString(sb, ps, col++, null);
            safeSetString(sb, ps, col++, artifact.contentType);
            safeSetLong(sb, ps, col++, artifact.contentLength);
            safeSetBoolean(sb, ps, col++, artifact.alternative);

            safeSetDate(sb, ps, col++, artifact.getLastModified(), UTC_CAL);
            safeSetDate(sb, ps, col++, artifact.getMaxLastModified(), UTC_CAL);
            safeSetInteger(sb, ps, col++, artifact.getStateCode());
            safeSetLong(sb, ps, col++, artifact.getID());

            if (sb != null)
                log.debug(sb.toString());
        }
    }

    private class PartPut implements EntityPut<Part>
    {
        private boolean update;
        private Part part;
        private List<CaomEntity> parents;

        PartPut(boolean update) { this.update = update; }

        public void setValue(Part p, List<CaomEntity> parents)
        {
            this.part = p;
            this.parents = parents;
        }

        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            String sql = null;
            if (update)
                sql = getUpdateSQL(Part.class);
            else
                sql = getInsertSQL(Part.class);
            PreparedStatement prep = conn.prepareStatement(sql);
            log.debug(sql);
            loadValues(prep);
            return prep;
        }
        private void loadValues(PreparedStatement ps)
            throws SQLException
        {
            if (part == null)
                throw new IllegalStateException("null part");
            
            // stack
            Observation obs = (Observation) parents.get(2);
            Plane plane = (Plane) parents.get(1);
            Artifact artifact = (Artifact) parents.get(0);

            StringBuilder sb = null;
            if (log.isDebugEnabled())
                sb = new StringBuilder();

            int col = 1;
            
            safeSetLong(sb, ps, col++, artifact.getID()); 
            safeSetLong(sb, ps, col++, plane.getID());
            safeSetLong(sb, ps, col++, obs.getID());
            safeSetDate(sb, ps, col++, Util.truncate(plane.metaRelease), UTC_CAL);
            
            safeSetString(sb, ps, col++, part.getName());

            if (part.productType != null)
                safeSetString(sb, ps, col++, part.productType.getValue());
            else
                safeSetString(sb, ps, col++, null);

            safeSetDate(sb, ps, col++, part.getLastModified(), UTC_CAL);
            safeSetDate(sb, ps, col++, part.getMaxLastModified(), UTC_CAL);
            safeSetInteger(sb, ps, col++, part.getStateCode());
            safeSetLong(sb, ps, col++, part.getID());

            if (sb != null)
                log.debug(sb.toString());
        }
    }

    private class ChunkPut implements EntityPut<Chunk>
    {
        private boolean update;
        private Chunk chunk;
        private List<CaomEntity> parents;

        ChunkPut(boolean update) { this.update = update; }

        public void setValue(Chunk c, List<CaomEntity> parents)
        {
            this.chunk = c;
            this.parents = parents;
        }

        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            String sql = null;
            if (update)
                sql = getUpdateSQL(Chunk.class);
            else
                sql = getInsertSQL(Chunk.class);
            PreparedStatement prep = conn.prepareStatement(sql);
            log.debug(sql);
            loadValues(prep);
            return prep;
        }
        private void loadValues(PreparedStatement ps)
            throws SQLException
        {
            if (chunk == null)
                throw new IllegalStateException("null chunk");
            
            // stack
            Observation obs = (Observation) parents.get(3);
            Plane plane = (Plane) parents.get(2);
            Artifact artifact = (Artifact) parents.get(1);
            Part part = (Part) parents.get(0);

            StringBuilder sb = null;
            if (log.isDebugEnabled())
                sb = new StringBuilder();

            int col = 1;
            safeSetLong(sb, ps, col++, part.getID());
            safeSetLong(sb, ps, col++, artifact.getID());
            safeSetLong(sb, ps, col++, plane.getID());
            safeSetLong(sb, ps, col++, obs.getID());
            safeSetDate(sb, ps, col++, Util.truncate(plane.metaRelease), UTC_CAL);
            
            if (chunk.productType != null)
                safeSetString(sb, ps, col++, chunk.productType.getValue());
            else
                safeSetString(sb, ps, col++, null);

            safeSetInteger(sb, ps, col++, chunk.naxis);
            safeSetInteger(sb, ps, col++, chunk.positionAxis1);
            safeSetInteger(sb, ps, col++, chunk.positionAxis2);
            safeSetInteger(sb, ps, col++, chunk.energyAxis);
            safeSetInteger(sb, ps, col++, chunk.timeAxis);
            safeSetInteger(sb, ps, col++, chunk.polarizationAxis);
            safeSetInteger(sb, ps, col++, chunk.observableAxis);

            if (chunk.position != null)
            {
                safeSetString(sb, ps, col++, chunk.position.getAxis().getAxis1().getCtype());
                safeSetString(sb, ps, col++, chunk.position.getAxis().getAxis1().getCunit());
                safeSetString(sb, ps, col++, chunk.position.getAxis().getAxis2().getCtype());
                safeSetString(sb, ps, col++, chunk.position.getAxis().getAxis2().getCunit());
                if (chunk.position.getAxis().error1 != null)
                {
                    safeSetDouble(sb, ps, col++, chunk.position.getAxis().error1.syser);
                    safeSetDouble(sb, ps, col++, chunk.position.getAxis().error1.rnder);
                }
                else
                {
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                }
                if (chunk.position.getAxis().error2 != null)
                {
                    safeSetDouble(sb, ps, col++, chunk.position.getAxis().error2.syser);
                    safeSetDouble(sb, ps, col++, chunk.position.getAxis().error2.rnder);
                }
                else
                {
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                }
                //range
                //safeSetString(sb, ps, col++, Util.encodeCoordRange2D(chunk.position.getAxis().range));
                col += safeSet(sb, ps, col, chunk.position.getAxis().range);

                // bounds
                safeSetString(sb, ps, col++, Util.encodeCoordBounds2D(chunk.position.getAxis().bounds));

                // function
                //safeSetString(sb, ps, col++, Util.encodeCoordFunction2D(chunk.position.getAxis().function));
                col += safeSet(sb, ps, col, chunk.position.getAxis().function);

                // other fields
                safeSetString(sb, ps, col++, chunk.position.coordsys);
                safeSetDouble(sb, ps, col++, chunk.position.equinox);
                safeSetDouble(sb, ps, col++, chunk.position.resolution);
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                //range
                //safeSetString(sb, ps, col++, null);
                col += safeSet(sb, ps, col, (CoordRange2D) null);
                // bounds
                safeSetString(sb, ps, col++, null);
                // function
                //safeSetString(sb, ps, col++, null);
                col += safeSet(sb, ps, col, (CoordFunction2D) null);
                // other fields
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
            }

            if (chunk.energy != null)
            {
                safeSetString(sb, ps, col++, chunk.energy.getAxis().getAxis().getCtype());
                safeSetString(sb, ps, col++, chunk.energy.getAxis().getAxis().getCunit());
                if (chunk.energy.getAxis().error != null)
                {
                    safeSetDouble(sb, ps, col++, chunk.energy.getAxis().error.syser);
                    safeSetDouble(sb, ps, col++, chunk.energy.getAxis().error.rnder);
                }
                else
                {
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                }
                //range
                //safeSetString(sb, ps, col++, Util.encodeCoordRange1D(chunk.energy.getAxis().range));
                col += safeSet(sb, ps, col, chunk.energy.getAxis().range);
                // bounds
                safeSetString(sb, ps, col++, Util.encodeCoordBounds1D(chunk.energy.getAxis().bounds));
                // function
                //safeSetString(sb, ps, col++, Util.encodeCoordFunction1D(chunk.energy.getAxis().function));
                col += safeSet(sb, ps, col, chunk.energy.getAxis().function);

                // other fields
                safeSetString(sb, ps, col++, chunk.energy.getSpecsys());
                safeSetString(sb, ps, col++, chunk.energy.ssysobs);
                safeSetString(sb, ps, col++, chunk.energy.ssyssrc);
                safeSetDouble(sb, ps, col++, chunk.energy.restfrq);
                safeSetDouble(sb, ps, col++, chunk.energy.restwav);
                safeSetDouble(sb, ps, col++, chunk.energy.velosys);
                safeSetDouble(sb, ps, col++, chunk.energy.zsource);
                safeSetDouble(sb, ps, col++, chunk.energy.velang);
                safeSetString(sb, ps, col++, chunk.energy.bandpassName);
                safeSetDouble(sb, ps, col++, chunk.energy.resolvingPower);
                if (chunk.energy.transition != null)
                {
                    safeSetString(sb, ps, col++, chunk.energy.transition.getSpecies());
                    safeSetString(sb, ps, col++, chunk.energy.transition.getTransition());
                }
                else
                {
                    safeSetString(sb, ps, col++, null);
                    safeSetString(sb, ps, col++, null);
                }
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                //range
                //safeSetString(sb, ps, col++, null);
                col += safeSet(sb, ps, col, (CoordRange1D) null);
                // bounds
                safeSetString(sb, ps, col++, null);
                // function
                //safeSetString(sb, ps, col++, null);
                col += safeSet(sb, ps, col, (CoordFunction1D) null);

                // other fields
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
            }

            if (chunk.time != null)
            {
                safeSetString(sb, ps, col++, chunk.time.getAxis().getAxis().getCtype());
                safeSetString(sb, ps, col++, chunk.time.getAxis().getAxis().getCunit());
                if (chunk.time.getAxis().error != null)
                {
                    safeSetDouble(sb, ps, col++, chunk.time.getAxis().error.syser);
                    safeSetDouble(sb, ps, col++, chunk.time.getAxis().error.rnder);
                }
                else
                {
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                }
                //range
                //safeSetString(sb, ps, col++, Util.encodeCoordRange1D(chunk.time.getAxis().range));
                col += safeSet(sb, ps, col, chunk.time.getAxis().range);
                // bounds
                safeSetString(sb, ps, col++, Util.encodeCoordBounds1D(chunk.time.getAxis().bounds));
                // function
                //safeSetString(sb, ps, col++, Util.encodeCoordFunction1D(chunk.time.getAxis().function));
                col += safeSet(sb, ps, col, chunk.time.getAxis().function);
                // other fields
                safeSetString(sb, ps, col++, chunk.time.timesys);
                safeSetString(sb, ps, col++, chunk.time.trefpos);
                safeSetDouble(sb, ps, col++, chunk.time.mjdref);
                safeSetDouble(sb, ps, col++, chunk.time.exposure);
                safeSetDouble(sb, ps, col++, chunk.time.resolution);
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                //range
                //safeSetString(sb, ps, col++, null);
                col += safeSet(sb, ps, col, (CoordRange1D) null);
                // bounds
                safeSetString(sb, ps, col++, null);
                // function
                //safeSetString(sb, ps, col++, null);
                col += safeSet(sb, ps, col, (CoordFunction1D) null);
                // other fields
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
            }

            if (chunk.polarization != null)
            {
                safeSetString(sb, ps, col++, chunk.polarization.getAxis().getAxis().getCtype());
                safeSetString(sb, ps, col++, chunk.polarization.getAxis().getAxis().getCunit());
                if (chunk.polarization.getAxis().error != null)
                {
                    safeSetDouble(sb, ps, col++, chunk.polarization.getAxis().error.syser);
                    safeSetDouble(sb, ps, col++, chunk.polarization.getAxis().error.rnder);
                }
                else
                {
                    safeSetDouble(sb, ps, col++, null);
                    safeSetDouble(sb, ps, col++, null);
                }
                //range
                //safeSetString(sb, ps, col++, Util.encodeCoordRange1D(chunk.polarization.getAxis().range));
                col += safeSet(sb, ps, col, chunk.polarization.getAxis().range);
                // bounds
                safeSetString(sb, ps, col++, Util.encodeCoordBounds1D(chunk.polarization.getAxis().bounds));
                // function
                //safeSetString(sb, ps, col++, Util.encodeCoordFunction1D(chunk.polarization.getAxis().function));
                col += safeSet(sb, ps, col, chunk.polarization.getAxis().function);
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                //range
                //safeSetString(sb, ps, col++, null);
                col += safeSet(sb, ps, col, (CoordRange1D) null);
                // bounds
                safeSetString(sb, ps, col++, null);
                // function
                //safeSetString(sb, ps, col++, null);
                col += safeSet(sb, ps, col, (CoordFunction1D) null);
                // other fields
            }

            if (chunk.observable != null)
            {
                safeSetString(sb, ps, col++, chunk.observable.getDependent().getAxis().getCtype());
                safeSetString(sb, ps, col++, chunk.observable.getDependent().getAxis().getCunit());
                safeSetLong(sb, ps, col++, chunk.observable.getDependent().getBin());
                if (chunk.observable.independent != null)
                {
                    safeSetString(sb, ps, col++, chunk.observable.independent.getAxis().getCtype());
                    safeSetString(sb, ps, col++, chunk.observable.independent.getAxis().getCunit());
                    safeSetLong(sb, ps, col++, chunk.observable.independent.getBin());
                }
                else
                {
                    safeSetString(sb, ps, col++, null);
                    safeSetString(sb, ps, col++, null);
                    safeSetLong(sb, ps, col++, null);
                }
            }
            else
            {
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetLong(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetString(sb, ps, col++, null);
                safeSetLong(sb, ps, col++, null);
            }

            safeSetDate(sb, ps, col++, chunk.getLastModified(), UTC_CAL);
            safeSetDate(sb, ps, col++, chunk.getMaxLastModified(), UTC_CAL);
            safeSetInteger(sb, ps, col++, chunk.getStateCode());
            safeSetLong(sb, ps, col++, chunk.getID());

            if (sb != null)
                log.debug(sb.toString());
        }

        private int safeSet(StringBuilder sb, PreparedStatement ps, int col, CoordRange1D r)
            throws SQLException
        {
            if (r != null)
            {
                safeSetDouble(sb, ps, col++, r.getStart().pix);
                safeSetDouble(sb, ps, col++, r.getStart().val);
                safeSetDouble(sb, ps, col++, r.getEnd().pix);
                safeSetDouble(sb, ps, col++, r.getEnd().val);
            }
            else
            {
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
            }
            return 4;
        }
        private int safeSet(StringBuilder sb, PreparedStatement ps, int col, CoordFunction1D f)
            throws SQLException
        {
            if (f != null)
            {
                safeSetLong(sb, ps, col++, f.getNaxis());
                safeSetDouble(sb, ps, col++, f.getRefCoord().pix);
                safeSetDouble(sb, ps, col++, f.getRefCoord().val);
                safeSetDouble(sb, ps, col++, f.getDelta());
            }
            else
            {
                safeSetLong(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
            }
            return 4;
        }
        private int safeSet(StringBuilder sb, PreparedStatement ps, int col, CoordRange2D r)
            throws SQLException
        {
            if (r != null)
            {
                safeSetDouble(sb, ps, col++, r.getStart().getCoord1().pix);
                safeSetDouble(sb, ps, col++, r.getStart().getCoord1().val);
                safeSetDouble(sb, ps, col++, r.getStart().getCoord2().pix);
                safeSetDouble(sb, ps, col++, r.getStart().getCoord2().val);
                safeSetDouble(sb, ps, col++, r.getEnd().getCoord1().pix);
                safeSetDouble(sb, ps, col++, r.getEnd().getCoord1().val);
                safeSetDouble(sb, ps, col++, r.getEnd().getCoord2().pix);
                safeSetDouble(sb, ps, col++, r.getEnd().getCoord2().val);
            }
            else
            {
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
            }
            return 8;
        }
        private int safeSet(StringBuilder sb, PreparedStatement ps, int col, CoordFunction2D f)
            throws SQLException
        {
            if (f != null)
            {
                safeSetLong(sb, ps, col++, f.getDimension().naxis1);
                safeSetLong(sb, ps, col++, f.getDimension().naxis2);
                safeSetDouble(sb, ps, col++, f.getRefCoord().getCoord1().pix);
                safeSetDouble(sb, ps, col++, f.getRefCoord().getCoord1().val);
                safeSetDouble(sb, ps, col++, f.getRefCoord().getCoord2().pix);
                safeSetDouble(sb, ps, col++, f.getRefCoord().getCoord2().val);
                safeSetDouble(sb, ps, col++, f.getCd11());
                safeSetDouble(sb, ps, col++, f.getCd12());
                safeSetDouble(sb, ps, col++, f.getCd21());
                safeSetDouble(sb, ps, col++, f.getCd22());
            }
            else
            {
                safeSetLong(sb, ps, col++, null);
                safeSetLong(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
                safeSetDouble(sb, ps, col++, null);
            }
            return 10;
        }
    }

    private class ReadAccessPut implements EntityPut<ReadAccess>
    {
        private boolean update;
        private ReadAccess ra;

        ReadAccessPut(boolean update) { this.update = update; }

        public void setValue(ReadAccess ra, List<CaomEntity> unused)
        {
            this.ra = ra;
        }

        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException
        {
            String sql = null;
            if (update)
                sql = getUpdateSQL(ra.getClass());
            else
                sql = getInsertSQL(ra.getClass());
            PreparedStatement prep = conn.prepareStatement(sql);
            log.debug(sql);
            loadValues(prep);
            return prep;
        }
        private void loadValues(PreparedStatement ps)
            throws SQLException
        {
            if (ra == null)
                throw new IllegalStateException("null read access");

            StringBuilder sb = null;
            if (log.isDebugEnabled())
                sb = new StringBuilder();

            int col = 1;
            safeSetLong(sb, ps, col++, ra.getAssetID());
            safeSetLong(sb, ps, col++, ra.getGroupID());
            safeSetDate(sb, ps, col++, ra.getLastModified(), UTC_CAL);
            safeSetInteger(sb, ps, col++, ra.getStateCode());
            safeSetLong(sb, ps, col++, ra.getID());

            if (sb != null)
                log.debug(sb.toString());
        }
    }

    protected void safeSetDate(StringBuilder sb, PreparedStatement ps, int col, Date val, Calendar cal)
        throws SQLException
    {
        if (val != null)
            ps.setTimestamp(col, new Timestamp(val.getTime()),cal);
        else
            ps.setNull(col, Types.TIMESTAMP);
        if (sb != null)
        {
            sb.append(val);
            sb.append(",");
        }
    }
    
    protected void safeSetString(StringBuilder sb, PreparedStatement ps, int col, String val)
        throws SQLException
    {
        if (val != null)
            ps.setString(col, val);
        else
            ps.setNull(col, Types.VARCHAR);
        if (sb != null)
        {
            sb.append(val);
            sb.append(",");
        }
    }
    
    protected void safeSetDouble(StringBuilder sb, PreparedStatement ps, int col, Double val)
        throws SQLException
    {
        if (val != null)
            ps.setDouble(col, val);
        else
            ps.setNull(col, Types.DOUBLE);
        if (sb != null)
        {
            sb.append(val);
            sb.append(",");
        }
    }
    
    protected void safeSetLong(StringBuilder sb, PreparedStatement ps, int col, Long val)
        throws SQLException
    {
        if (val != null)
            ps.setLong(col, val);
        else
            ps.setNull(col, Types.BIGINT);
        if (sb != null)
        {
            sb.append(val);
            sb.append(",");
        }
    }
    
    protected void safeSetInteger(StringBuilder sb, PreparedStatement ps, int col, Integer val)
        throws SQLException
    {
        if (val != null)
            ps.setLong(col, val);
        else
            ps.setNull(col, Types.INTEGER);
        if (sb != null)
        {
            sb.append(val);
            sb.append(",");
        }
    }
    
    protected void safeSetBoolean(StringBuilder sb, PreparedStatement ps, int col, Boolean val)
        throws SQLException
    {
        if (useIntegerForBoolean)
        {
            Integer ival = null;
            if (val != null)
            {
                if (val.booleanValue())
                    ival = new Integer(1);
                else
                    ival = new Integer(0);
            }
            safeSetInteger(sb, ps, col, ival);
            return;
        }
        
        if (val != null)
            ps.setBoolean(col, val);
        else
            ps.setNull(col, Types.BOOLEAN);
        if (sb != null)
        {
            sb.append(val);
            sb.append(",");
        }
    }

    protected void safeSetBinary(StringBuilder sb, PreparedStatement ps, int col, byte[] val)
        throws SQLException
    {
        if (val == null)
        {
            ps.setBytes(col, val);
            if (sb != null)
                sb.append("null,");
        }
        else
        {
            ps.setNull(col, Types.VARBINARY);
            if (sb != null)
            {
                sb.append("byte[");
                sb.append(val.length);
                sb.append("],");
            }
        }
    }

    protected void safeSetPoint(StringBuilder sb, PreparedStatement ps, int col, Point val)
        throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    protected void safeSetPolygon(StringBuilder sb, PreparedStatement ps, int col, Polygon val)
        throws SQLException
    {
        throw new UnsupportedOperationException();
    }
    
    public String literal(Object obj)
    {
        if (obj == null)
            return "NULL";

        if (obj instanceof Number)
            return obj.toString();

        if (obj instanceof String)
            return "'" + obj + "'";

        throw new IllegalArgumentException("unsupported literal: " + obj.getClass().getName());
    }

    protected String literal(boolean value)
    {
        return Boolean.toString(value);
    }

    protected String literal(double value)
    {
        return Double.toString(value);
    }

    protected String literal(float value)
    {
        return Float.toString(value);
    }

    protected String literal(int value)
    {
        return Integer.toString(value);
    }

    protected String literal(long value)
    {
        return Long.toString(value);
    }

    protected Class getClassFromUtype(String utype)
        throws ClassNotFoundException
    {
        int i = utype.indexOf('.');
        String simpleName = utype.substring(0,i);
        return Class.forName(BASE_PKG + "." + simpleName);
    }

    public String getColumnName(String utype)
    {
        log.debug("getColumnName: " + utype);
        try
        {
            int i = utype.indexOf('.');
            String simpleName = utype.substring(0,i);
            Class c = getClassFromUtype(utype);
            String alias = simpleName;
            if (c != null)
            {
                log.debug("getColumnName: class = " + c.getName());
                alias = getAlias(c);
            }
            utype = utype.substring(i+1);
            utype = Util.replaceAll(utype, '.', '_');
            log.debug("alias: " + alias +  "  utype: " + utype);
            return alias + "." + utype;
        }
        catch(ClassNotFoundException cex)
        {
            throw new RuntimeException("failed to map utype ("+utype+") -> Class -> alias", cex);
        }
    }

    private static Object NULL_VALUE = null; // a type for calling literal(null)
    String getColumns(Class c)
    {
        return getColumns(c, null);
    }
    String getColumns(Class c, String alias)
    {
        Object obj = columnMap.get(c);
        if (obj != null)
        {
            StringBuilder sb = new StringBuilder();
            String[] cols = (String[]) obj;
            if (alias == null)
                alias = getAlias(c);
            for (int i=0; i<cols.length; i++)
            {
                if (cols[i] == null)
                    sb.append(literal(NULL_VALUE));
                else
                {
                    sb.append(alias);
                    sb.append(".");
                    sb.append(cols[i]);
                }
                sb.append(",");
            }
            return sb.substring(0, sb.length() - 1); // strip trailing comma
        }
        return null;
    }

    public String getTable(Class c)
    {
        String tabName = (String) tableMap.get(c);
        StringBuilder sb = new StringBuilder();
        if (database != null)
        {
            sb.append(database);
            sb.append(".");
        }
        if (schema != null)
        {
            sb.append(schema);
            sb.append(".");
        }
        sb.append(tabName);
        return sb.toString();
    }
    
    public String getAlias(Class c)
    {
        return (String) aliasMap.get(c);
    }
    
    protected String getFrom(Class c)
    {
        String tab = getTable(c);
        //if (tab.startsWith("(") && tab.endsWith(")"))
        //    return tab;
        StringBuilder sb = new StringBuilder();
        sb.append(tab);
        sb.append(" AS ");
        sb.append(getAlias(c));
        return sb.toString();
    }
    
    public String getFrom(Class c, int depth, boolean skeleton)
    {
        log.debug("getFrom: " + c + ", depth = " + depth);
        
        String a1 = getAlias(c);
        String f1 = getFrom(c);
        
        if (depth == 1)
            return f1;
        
        StringBuilder sb = new StringBuilder();
        sb.append(f1);
        Class child = null;
        if (Observation.class.isAssignableFrom(c) || ObservationSkeleton.class.isAssignableFrom(c))
        {
            if (skeleton)
                child = PlaneSkeleton.class;
            else
                child = Plane.class;
            log.debug("getFrom: observation JOIN plane");
            // join to plane
            String a2 = getAlias(child);
            String f2 = getFrom(child, depth-1, skeleton); // recursive
            sb.append(" LEFT OUTER JOIN ");
            sb.append(f2);
            sb.append(" ON ");
            sb.append(a1);
            sb.append(".obsID = ");
            sb.append(a2);
            sb.append(".obsID");
        }
        else if (Plane.class.equals(c) || PlaneSkeleton.class.equals(c))
        {
            if (skeleton)
                child = ArtifactSkeleton.class;
            else
                child = Artifact.class;
            log.debug("getFrom: plane JOIN artifact");
            // join to artifact
            String a2 = getAlias(child);
            String f2 = getFrom(child, depth-1, skeleton);
            sb.append(" LEFT OUTER JOIN ");
            sb.append(f2);
            sb.append(" ON ");
            sb.append(a1);
            sb.append(".planeID = ");
            sb.append(a2);
            sb.append(".planeID");
        }
        else if (Artifact.class.equals(c) || ArtifactSkeleton.class.equals(c))
        {
            if (skeleton)
                child = PartSkeleton.class;
            else
                child = Part.class;
            log.debug("getFrom: artifact JOIN part");
            // join to artifact
            String a2 = getAlias(child);
            String f2 = getFrom(child, depth-1,skeleton);
            sb.append(" LEFT OUTER JOIN ");
            sb.append(f2);
            sb.append(" ON ");
            sb.append(a1);
            sb.append(".artifactID = ");
            sb.append(a2);
            sb.append(".artifactID");
        }
        else if (Part.class.equals(c) || PartSkeleton.class.equals(c))
        {
            if (skeleton)
                child = ChunkSkeleton.class;
            else
                child = Chunk.class;
            log.debug("getFrom: part JOIN chunk");
            // join to artifact
            String a2 = getAlias(child);
            String f2 = getFrom(child, depth-1, skeleton);
            sb.append(" LEFT OUTER JOIN ");
            sb.append(f2);
            sb.append(" ON ");
            sb.append(a1);
            sb.append(".partID = ");
            sb.append(a2);
            sb.append(".partID");
        }
        return sb.toString();
    }
    
    private String getOrderColumns(int depth)
    {
        StringBuilder sb = new StringBuilder();
        if (depth > 1)
        {
            sb.append(getAlias(Observation.class));
            sb.append(".obsID");
        }
        if (depth > 2)
        {
            sb.append(",");
            sb.append(getAlias(Plane.class));
            sb.append(".planeID");
        }
        if (depth > 3)
        {
            sb.append(",");
            sb.append(getAlias(Artifact.class));
            sb.append(".artifactID");
        }
        if (depth > 4)
        {
            sb.append(",");
            sb.append(getAlias(Part.class));
            sb.append(".partID");
        }
        if (sb.length() > 0)
            return sb.toString();
        return null;
    }
    private String getSkeletonOrderColumns(int depth)
    {
        StringBuilder sb = new StringBuilder();
        if (depth > 1)
        {
            sb.append(getAlias(ObservationSkeleton.class));
            sb.append(".obsID");
        }
        if (depth > 2)
        {
            sb.append(",");
            sb.append(getAlias(PlaneSkeleton.class));
            sb.append(".planeID");
        }
        if (depth > 3)
        {
            sb.append(",");
            sb.append(getAlias(ArtifactSkeleton.class));
            sb.append(".artifactID");
        }
        if (depth > 4)
        {
            sb.append(",");
            sb.append(getAlias(PartSkeleton.class));
            sb.append(".partID");
        }
        if (sb.length() > 0)
            return sb.toString();
        return null;
    }


    protected StringBuilder getObservationSelect(int depth, boolean skeleton)
    {
        log.debug("getObservationSelect: " + depth + "," + skeleton);
        StringBuilder sb = getObservationSelectList(depth, skeleton);
        sb.append(" FROM ");
        if (skeleton)
            sb.append(getFrom(ObservationSkeleton.class, depth, skeleton));
        else
            sb.append(getFrom(Observation.class, depth, skeleton));
        return sb;
    }
    protected StringBuilder getObservationSelectList(int depth, boolean skeleton)
    {
        StringBuilder sb = new StringBuilder();
        if (skeleton)
            sb.append(getColumns(ObservationSkeleton.class));
        else
            sb.append(getColumns(Observation.class));
        
        // extra columns depend on depth: these sets of columns could be 
        // refactored into a recursive function, getColumnList(Class, int)
        if (depth > 1)
        {
            sb.append(",");
            if (skeleton)
                sb.append(getColumns(PlaneSkeleton.class));
            else
                sb.append(getColumns(Plane.class));
        }
        // Metric(s)?
        if (depth > 2)
        {
            sb.append(",");
            if (skeleton)
                sb.append(getColumns(ArtifactSkeleton.class));
            else
                sb.append(getColumns(Artifact.class));
        }
        if (depth > 3)
        {
            sb.append(",");
            if (skeleton)
                sb.append(getColumns(PartSkeleton.class));
            else
                sb.append(getColumns(Part.class));
        }
        if (depth > 4)
        {
            sb.append(",");
            if (skeleton)
                sb.append(getColumns(ChunkSkeleton.class));
            else
                sb.append(getColumns(Chunk.class));
        }
        return sb;
    }
    
    protected StringBuilder getEntitySelect(Class c)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getColumns(c));
        sb.append(" FROM ");
        sb.append(getFrom(c));
        return sb;
    }
    
    public BaseObservationExtractor getObservationExtractor()
    {
        return new BaseObservationExtractor(this);
    }

    public RowMapper getReadAccessMapper(Class<? extends ReadAccess> c)
    {
        return new ReadAccessMapper(c);
    }

    public Class<? extends Skeleton> getSkeletonClass(Class c)
    {
        if (c.equals(ObservationMetaReadAccess.class))
            return ObservationMetaReadAccessSkeleton.class;
        
        if (c.equals(PlaneMetaReadAccess.class))
            return PlaneMetaReadAccessSkeleton.class;
        
        if (c.equals(PlaneDataReadAccess.class))
            return PlaneDataReadAccessSkeleton.class;
        
        throw new UnsupportedOperationException("getSkeletonClass: " + c.getName());
    }
    
    public ResultSetExtractor getSkeletonExtractor(Class<? extends Skeleton> c)
    {
        if (c.equals(ObservationSkeleton.class))
            return new ObservationSkeletonExtractor();
        
        if (c.equals(ObservationMetaReadAccessSkeleton.class))
            return new SkeletonExtractor(ObservationMetaReadAccessSkeleton.class);
        
        if (c.equals(PlaneMetaReadAccessSkeleton.class))
            return new SkeletonExtractor(PlaneMetaReadAccessSkeleton.class);
        
        if (c.equals(PlaneDataReadAccessSkeleton.class))
            return new SkeletonExtractor(PlaneDataReadAccessSkeleton.class);
        
        throw new UnsupportedOperationException("getSkeletonExtractor: " + c.getName());
    }

    public RowMapper getDeletedEntityMapper(Class<? extends DeletedEntity> c)
    {
        return new DeletedEntityMapper(c);
    }

    public RowMapper getTimestampRowMapper()
    {
        return new TimestampRowMapper(UTC_CAL);
    }
    
    private static class ClassComp implements Comparator<Class>
    {
        public int compare(Class o1, Class o2)
        {
            Class c1 = (Class) o1;
            Class c2 = (Class) o2;
            return c1.getSimpleName().compareTo(c2.getSimpleName());
        }
    }
    
    PartialRowMapper<Observation> getObservationMapper() { return new ObservationMapper(); }
    PartialRowMapper<Plane> getPlaneMapper() { return new PlaneMapper(); }
    PartialRowMapper<Artifact> getArtifactMapper() { return new ArtifactMapper(); }
    PartialRowMapper<Part> getPartMapper() { return new PartMapper(); }
    PartialRowMapper<Chunk> getChunkMapper() { return new ChunkMapper(); }

    class ObservationMapper implements PartialRowMapper<Observation>
    {
        private int columnCount;

        /**
         * @return the number of columns consumed mapping the last Observation
         */
        public int getColumnCount()
        {
            return columnMap.get(Observation.class).length;
        }

        public Object mapRow(ResultSet rs, int row)
            throws SQLException
        {
            return mapRow(rs, row, 1);
        }

        /**
         * Map columns from the current row into an Observation, starting at
         * the specified column offset.
         *
         * @param rs
         * @param row
         * @param col JDBC column offset where observation columns are located
         * @return
         * @throws java.sql.SQLException
         */
        public Observation mapRow(ResultSet rs, int row, int col)
            throws SQLException
        {
            // first column is a constant that dictates the type
            String typeCode = rs.getString(col++);
            if (typeCode == null)
                return null;

            String collection = rs.getString(col++);
            log.debug("found: collection = " + collection);
            String observationID = rs.getString(col++);
            log.debug("found: observationID = " + observationID);
            Algorithm algorithm = new Algorithm(rs.getString(col++));
            log.debug("found: algorithm = " + algorithm);

            Observation o = null;
            if (SIMPLE_TYPE.equals(typeCode))
                o = new SimpleObservation(collection, observationID);
            else if (COMPOSITE_TYPE.equals(typeCode))
                o = new CompositeObservation(collection, observationID, algorithm);

            o.type = rs.getString(col++);
            String intent = rs.getString(col++);
            log.debug("found: intent = " + intent);
            if (intent != null)
                o.intent = ObservationIntentType.toValue(intent);

            o.sequenceNumber = Util.getInteger(rs, col++);
            o.metaRelease = Util.getRoundedDate(rs, col++, UTC_CAL);

            String pid = rs.getString(col++);
            log.debug("found proposal.id = " + pid);
            if (pid != null)
            {
                o.proposal = new Proposal(pid);
                o.proposal.pi = rs.getString(col++);
                o.proposal.project = rs.getString(col++);
                o.proposal.title = rs.getString(col++);
                Util.decodeListString(rs.getString(col++), o.proposal.getKeywords());
                log.debug("found: " + o.proposal);
            }
            else
            {
                skipAndLog(rs, col, 4);
                col += 4; // skip
            }

            String targ = rs.getString(col++);
            log.debug("found target.name = " + targ);
            if (targ != null)
            {
                
                o.target = new Target(targ);
                String tt = rs.getString(col++);
                if (tt != null)
                    o.target.type = TargetType.toValue(tt);
                o.target.standard = Util.getBoolean(rs, col++);
                o.target.redshift = Util.getDouble(rs, col++);
                Util.decodeListString(rs.getString(col++), o.target.getKeywords());
                log.debug("found: " + o.target);
            }
            else
            {
                skipAndLog(rs, col, 4);
                col += 4; // skip
            }

            String tn = rs.getString(col++);
            log.debug("found o.telescope.name = " + tn);
            if (tn != null)
            {
                o.telescope = new Telescope(tn);
                o.telescope.geoLocationX = Util.getDouble(rs, col++);
                o.telescope.geoLocationY = Util.getDouble(rs, col++);
                o.telescope.geoLocationZ = Util.getDouble(rs, col++);
                Util.decodeListString(rs.getString(col++), o.telescope.getKeywords());
                log.debug("found: " + o.telescope);
            }
            else
            {
                skipAndLog(rs, col, 4);
                col += 4; // skip
            }

            String in = rs.getString(col++);
            log.debug("found o.instrument.name = " + in);
            if (in != null)
            {
                o.instrument = new Instrument(in);
                Util.decodeListString(rs.getString(col++), o.instrument.getKeywords());
                log.debug("found: " + o.instrument);
            }
            else
            {
                skipAndLog(rs, col, 1);
                col += 1; // skip
            }

            Environment e = new Environment();
            e.seeing = Util.getDouble(rs, col++);
            e.humidity = Util.getDouble(rs, col++);
            e.elevation = Util.getDouble(rs, col++);
            e.tau = Util.getDouble(rs, col++);
            e.wavelengthTau = Util.getDouble(rs, col++);
            e.ambientTemp = Util.getDouble(rs, col++);
            e.photometric = Util.getBoolean(rs, col++);

            if (e.seeing != null || e.humidity != null || e.elevation != null
                    ||  e.tau != null || e.wavelengthTau != null || e.ambientTemp != null
                    || e.photometric != null)
            {
                log.debug("found Environment: " + e.seeing + "," + e.photometric);
                o.environment = e;
            }

            if (o instanceof CompositeObservation)
            {
                CompositeObservation co = (CompositeObservation) o;
                Util.decodeObservationURIs(rs.getString(col++), co.getMembers());
            }
            else
            {
                skipAndLog(rs, col, 1);
                col += 1; // skip
            }

            if (persistTransientState)
            {
                 // skip them on read: wasteful but simpler code for now
                // TODO: skip them when creating the select-list instead
                col+= numComputedObservationColumns;
            }

            Date lastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: observation.lastModified = " + dateFormat.format(lastModified));
            Date maxLastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: observation.maxLastModified = " + dateFormat.format(maxLastModified));
            Integer stateCode = Util.getInteger(rs, col++);
            log.debug("found: observation.stateCode = " + stateCode);
            Long id = Util.getLong(rs, col++);
            log.debug("found: observation.id = " + id);

            Util.assignID(o, id);
            //Util.assignStateCode(o, stateCode);
            Util.assignLastModified(o, lastModified, "lastModified");
            Util.assignLastModified(o, maxLastModified, "maxLastModified");

            return o;
        }

        private void skipAndLog(ResultSet rs, int col, int num)
            throws SQLException
        {
            for (int i=0; i<num; i++)
                log.debug("skip: " + rs.getObject(col++));
        }
    }

    class PlaneMapper implements PartialRowMapper<Plane>
    {
        private Calendar UTC_CAL = Calendar.getInstance(DateUtil.UTC);

        public int getColumnCount()
        {
            return columnMap.get(Plane.class).length;
        }

        public Object mapRow(ResultSet rs, int row)
            throws SQLException
        {
            return mapRow(rs, row, 1);
        }

        /**
         * Map columns from the current row into a Plane, starting at the
         * specified column offset.
         *
         * @param rs
         * @param row
         * @param col JDBC column offset where plane columns are located
         * @return
         * @throws java.sql.SQLException
         */
        public Plane mapRow(ResultSet rs, int row, int col)
            throws SQLException
        {
            Long obsID = Util.getLong(rs, col++); // FK
            if (obsID == null)
                return null;

            String productID = rs.getString(col++);
            if (productID == null)
                return null;
            log.debug("found p.productID = " + productID);

            Plane p = new Plane(productID);

            p.metaRelease = Util.getRoundedDate(rs, col++, UTC_CAL);
            log.debug("found p.metaRelease = " + p.metaRelease);
            p.dataRelease = Util.getRoundedDate(rs, col++, UTC_CAL);
            log.debug("found p.dataRelease = " + p.dataRelease);

            String dpt = rs.getString(col++);
            log.debug("found p.dataProductType = " + dpt);
            if (dpt != null)
                p.dataProductType = DataProductType.toValue(dpt);

            Integer cl = Util.getInteger(rs, col++);
            log.debug("found p.calibrationLevel = " + cl);
            if (cl != null)
                p.calibrationLevel = CalibrationLevel.toValue(cl.intValue());

            String pname = rs.getString(col++);
            log.debug("found p.provenance.name = " + pname);
            if (pname != null)
            {
                p.provenance = new Provenance(pname);
                p.provenance.reference = Util.getURI(rs, col++);
                log.debug("found p.provenance.reference = " + p.provenance.reference);
                p.provenance.version = rs.getString(col++);
                log.debug("found p.provenance.version = " + p.provenance.version);
                p.provenance.project = rs.getString(col++);
                log.debug("found p.provenance.project = " + p.provenance.project);
                p.provenance.producer = rs.getString(col++);
                log.debug("found p.provenance.producer = " + p.provenance.producer);
                p.provenance.runID = rs.getString(col++);
                log.debug("found p.provenance.runID = " + p.provenance.runID);
                p.provenance.lastExecuted = Util.getRoundedDate(rs, col++, UTC_CAL);
                log.debug("found p.provenance.lastExecuted = " + p.provenance.lastExecuted);
                Util.decodePlaneURIs(rs.getString(col++), p.provenance.getInputs());
                log.debug("found p.provenance.inpts: " + p.provenance.getInputs().size());
                Util.decodeListString(rs.getString(col++), p.provenance.getKeywords());
                log.debug("found p.provenance.keywords: " + p.provenance.getKeywords().size());
            }
            else
                col += 8;

            Metrics m = new Metrics();
            m.sourceNumberDensity = Util.getDouble(rs, col++);
            m.background = Util.getDouble(rs, col++);
            m.backgroundStddev = Util.getDouble(rs, col++);
            m.fluxDensityLimit = Util.getDouble(rs, col++);
            m.magLimit = Util.getDouble(rs, col++);
            if (m.sourceNumberDensity != null || m.background != null || m.backgroundStddev != null
                    || m.fluxDensityLimit != null || m.magLimit != null)
                p.metrics = m;

            if (persistTransientState)
            {
                 // skip them on read: wasteful but simpler code for now
                // TODO: skip them when creating the select-list instead
                col+= numComputedPlaneColumns;
            }

            Date lastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: plane.lastModified = " + lastModified);
            Date maxLastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: plane.maxLastModified = " + dateFormat.format(maxLastModified));
            Integer stateCode = Util.getInteger(rs, col++);
            log.debug("found: plane.stateCode = " + stateCode);
            Long id = Util.getLong(rs, col++);
            log.debug("found: plane.id = " + id);

            Util.assignID(p, id);
            //Util.assignStateCode(p, stateCode);
            Util.assignLastModified(p, lastModified, "lastModified");
            Util.assignLastModified(p, maxLastModified, "maxLastModified");
            
            return p;
        }
    }

    class ArtifactMapper implements PartialRowMapper<Artifact>
    {
        public int getColumnCount()
        {
            return columnMap.get(Artifact.class).length;
        }
        
        public Object mapRow(ResultSet rs, int row) 
            throws SQLException
        {
            return mapRow(rs, row, 1);
        }
        
        /**
         * Map columns from the current row into an Artifact, starting at the 
         * specified column offset.
         * 
         * @param rs
         * @param row
         * @param col JDBC column offset where plane columns are located
         * @return
         * @throws java.sql.SQLException
         */
        public Artifact mapRow(ResultSet rs, int row, int col)
            throws SQLException
        {
            Long planeID = Util.getLong(rs, col++); // FK
            if (planeID == null)
                return null;
            
            col += 2; // skip ancestor(s) and metaRelease

            URI uri = Util.getURI(rs, col++);
            log.debug("found a.uri = " + uri);
            Artifact a = new Artifact(uri);

            String pt = rs.getString(col++);
            log.debug("found a.productType = " + pt);
            if (pt != null)
                a.productType = ProductType.toValue(pt);

            a.contentType = rs.getString(col++);
            log.debug("found a.contentType = " + a.contentType);
            a.contentLength = Util.getLong(rs, col++);
            log.debug("found a.contentLength = " + a.contentLength);
            a.alternative = rs.getBoolean(col++);
            log.debug("found a.alternative = " + a.alternative);
            
            Date lastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found artifact.lastModified = " + lastModified);
            Date maxLastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: artifact.maxLastModified = " + dateFormat.format(maxLastModified));
            Integer stateCode = Util.getInteger(rs, col++);
            log.debug("found: artifact.stateCode = " + stateCode);
            Long id = Util.getLong(rs, col++);
            log.debug("found artifact.id = " + id);
            
            Util.assignID(a, id);
            //Util.assignStateCode(a, stateCode);
            Util.assignLastModified(a, lastModified, "lastModified");
            Util.assignLastModified(a, maxLastModified, "maxLastModified");

            return a;
        }
    }

    class PartMapper implements PartialRowMapper<Part>
    {
        public int getColumnCount()
        {
            return columnMap.get(Part.class).length;
        }

        public Object mapRow(ResultSet rs, int row)
            throws SQLException
        {
            return mapRow(rs, row, 1);
        }

        /**
         * Map columns from the current row into an Artifact, starting at the
         * specified column offset.
         *
         * @param rs
         * @param row
         * @param col JDBC column offset where plane columns are located
         * @return
         * @throws java.sql.SQLException
         */
        public Part mapRow(ResultSet rs, int row, int col)
            throws SQLException
        {
            Long artifactID = Util.getLong(rs, col++); // FK
            if (artifactID == null)
                return null;
            
            col += 3; // skip ancestor(s) and metaRelease

            String name = rs.getString(col++);
            Part p = new Part(name);
            log.debug("found part: " + p);

            String pt = rs.getString(col++);
            log.debug("found p.productType = " + pt);
            if (pt != null)
                p.productType = ProductType.toValue(pt);

            
            Date lastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: part.lastModified = " + lastModified);
            Date maxLastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: part.maxLastModified = " + dateFormat.format(maxLastModified));
            Integer stateCode = Util.getInteger(rs, col++);
            log.debug("found: part.stateCode = " + stateCode);
            Long id = Util.getLong(rs, col++);
            log.debug("found: part.id = " + id);
            
            Util.assignID(p, id);
            //Util.assignStateCode(p, stateCode);
            Util.assignLastModified(p, lastModified, "lastModified");
            Util.assignLastModified(p, maxLastModified, "maxLastModified");
            
            return p;
        }
    }

    class ChunkMapper implements PartialRowMapper<Chunk>
    {
        public int getColumnCount()
        {
            return columnMap.get(Chunk.class).length;
        }

        public Object mapRow(ResultSet rs, int row)
            throws SQLException
        {
            return mapRow(rs, row, 1);
        }

        /**
         * Map columns from the current row into an Artifact, starting at the
         * specified column offset.
         *
         * @param rs
         * @param row
         * @param col JDBC column offset where plane columns are located
         * @return
         * @throws java.sql.SQLException
         */
        public Chunk mapRow(ResultSet rs, int row, int col)
            throws SQLException
        {
            Long partID = Util.getLong(rs, col++); // FK
            if (partID == null)
                return null;
            
            col += 4; // skip ancestor(s) and metaRelease
            
            Chunk c = new Chunk();

            String pt = rs.getString(col++);
            log.debug("found c.productType = " + pt);
            if (pt != null)
                c.productType = ProductType.toValue(pt);

            c.naxis = Util.getInteger(rs, col++);
            c.positionAxis1 = Util.getInteger(rs, col++);
            c.positionAxis2 = Util.getInteger(rs, col++);
            c.energyAxis = Util.getInteger(rs, col++);
            c.timeAxis = Util.getInteger(rs, col++);
            c.polarizationAxis = Util.getInteger(rs, col++);
            c.observableAxis = Util.getInteger(rs, col++);

            // position
            String posctype1 = rs.getString(col++);
            String poscunit1 = rs.getString(col++);
            String posctype2 = rs.getString(col++);
            String poscunit2 = rs.getString(col++);
            Double e1s = Util.getDouble(rs, col++);
            Double e1r = Util.getDouble(rs, col++);
            Double e2s = Util.getDouble(rs, col++);
            Double e2r = Util.getDouble(rs, col++);
            CoordRange2D posrange = null; //Util.decodeCoordRange2D(rs.getString(col++));
            Double start1pix = Util.getDouble(rs, col++);
            Double start1val = Util.getDouble(rs, col++);
            Double start2pix = Util.getDouble(rs, col++);
            Double start2val = Util.getDouble(rs, col++);
            Double end1pix = Util.getDouble(rs, col++);
            Double end1val = Util.getDouble(rs, col++);
            Double end2pix = Util.getDouble(rs, col++);
            Double end2val = Util.getDouble(rs, col++);
            if (start1pix != null)
                posrange = new CoordRange2D(
                        new Coord2D(new RefCoord(start1pix, start1val), new RefCoord(start2pix, start2val)),
                        new Coord2D(new RefCoord(end1pix, end1val), new RefCoord(end2pix, end2val)));
            CoordBounds2D posbounds = Util.decodeCoordBounds2D(rs.getString(col++));
            CoordFunction2D posfunction = null; //Util.decodeCoordFunction2D(rs.getString(col++));
            Long naxis1 = Util.getLong(rs, col++);
            Long naxis2 = Util.getLong(rs, col++);
            Double c1pix = Util.getDouble(rs, col++);
            Double c1val = Util.getDouble(rs, col++);
            Double c2pix = Util.getDouble(rs, col++);
            Double c2val = Util.getDouble(rs, col++);
            Double cd11 = Util.getDouble(rs, col++);
            Double cd12 = Util.getDouble(rs, col++);
            Double cd21 = Util.getDouble(rs, col++);
            Double cd22 = Util.getDouble(rs, col++);
            if (naxis1 != null)
                posfunction = new CoordFunction2D(new Dimension2D(naxis1, naxis2),
                        new Coord2D(new RefCoord(c1pix, c1val), new RefCoord(c2pix, c2val)),
                        cd11, cd12, cd21, cd22);

            String coordsys = rs.getString(col++);
            Double equinox = Util.getDouble(rs, col++);
            Double posres = Util.getDouble(rs, col++);
            if (posctype1 != null)
            {
                CoordAxis2D axis = new CoordAxis2D(new Axis(posctype1, poscunit1), new Axis(posctype2, poscunit2));
                if (e1s != null || e1r != null)
                    axis.error1 = new CoordError(e1s, e1r);
                if (e2s != null || e2r != null)
                    axis.error2 = new CoordError(e1s, e2r);
                axis.range = posrange;
                axis.bounds = posbounds;
                axis.function = posfunction;
                c.position = new SpatialWCS(axis);
                c.position.coordsys = coordsys;
                c.position.equinox = equinox;
                c.position.resolution = posres;
            }

            // energy
            String enctype = rs.getString(col++);
            String encunit = rs.getString(col++);
            Double enes = Util.getDouble(rs, col++);
            Double ener = Util.getDouble(rs, col++);
            CoordRange1D enrange = null; //Util.decodeCoordRange1D(rs.getString(col++));
            Double pix1 = Util.getDouble(rs, col++);
            Double val1 = Util.getDouble(rs, col++);
            Double pix2 = Util.getDouble(rs, col++);
            Double val2 = Util.getDouble(rs, col++);
            if (pix1 != null)
                enrange = new CoordRange1D(new RefCoord(pix1, val1), new RefCoord(pix2, val2));

            CoordBounds1D enbounds = Util.decodeCoordBounds1D(rs.getString(col++));
            CoordFunction1D enfunction = null; //Util.decodeCoordFunction1D(rs.getString(col++));
            Long naxis = Util.getLong(rs, col++);
            Double pix = Util.getDouble(rs, col++);
            Double val = Util.getDouble(rs, col++);
            Double delta = Util.getDouble(rs, col++);
            if (naxis != null)
                enfunction = new CoordFunction1D(naxis, delta, new RefCoord(pix, val));
            String specsys = rs.getString(col++);
            String ssysobs = rs.getString(col++);
            String ssyssrc = rs.getString(col++);
            Double restfrq = Util.getDouble(rs, col++);
            Double restwav = Util.getDouble(rs, col++);
            Double velosys = Util.getDouble(rs, col++);
            Double zsource = Util.getDouble(rs, col++);
            Double velang = Util.getDouble(rs, col++);
            String bandpassName = rs.getString(col++);
            Double enres = Util.getDouble(rs, col++);
            String species = rs.getString(col++);
            String trans = rs.getString(col++);
            if (enctype != null)
            {
                CoordAxis1D axis = new CoordAxis1D(new Axis(enctype, encunit));
                if (enes != null || ener != null)
                    axis.error = new CoordError(enes, ener);
                axis.range = enrange;
                axis.bounds = enbounds;
                axis.function = enfunction;
                c.energy = new SpectralWCS(axis, specsys);
                c.energy.ssysobs = ssysobs;
                c.energy.ssyssrc = ssyssrc;
                c.energy.restfrq = restfrq;
                c.energy.restwav = restwav;
                c.energy.velosys = velosys;
                c.energy.zsource = zsource;
                c.energy.velang = velang;
                c.energy.bandpassName = bandpassName;
                c.energy.resolvingPower = enres;
                if (species != null)
                    c.energy.transition = new EnergyTransition(species, trans);
            }

            // time
            String tctype = rs.getString(col++);
            String tcunit = rs.getString(col++);
            Double tes = Util.getDouble(rs, col++);
            Double ter = Util.getDouble(rs, col++);
            CoordRange1D trange = null; // Util.decodeCoordRange1D(rs.getString(col++));
            pix1 = Util.getDouble(rs, col++);
            val1 = Util.getDouble(rs, col++);
            pix2 = Util.getDouble(rs, col++);
            val2 = Util.getDouble(rs, col++);
            if (pix1 != null)
                trange = new CoordRange1D(new RefCoord(pix1, val1), new RefCoord(pix2, val2));

            CoordBounds1D tbounds = Util.decodeCoordBounds1D(rs.getString(col++));
            CoordFunction1D tfunction = null; // Util.decodeCoordFunction1D(rs.getString(col++));
            naxis = Util.getLong(rs, col++);
            pix = Util.getDouble(rs, col++);
            val = Util.getDouble(rs, col++);
            delta = Util.getDouble(rs, col++);
            if (naxis != null)
                tfunction = new CoordFunction1D(naxis, delta, new RefCoord(pix, val));

            String timesys = rs.getString(col++);
            String trefpos = rs.getString(col++);
            Double mjdref = Util.getDouble(rs, col++);
            Double exposure = Util.getDouble(rs, col++);
            Double tres = Util.getDouble(rs, col++);
            if (tctype != null)
            {
                CoordAxis1D axis = new CoordAxis1D(new Axis(tctype, tcunit));
                if (tes != null || ter != null)
                    axis.error = new CoordError(tes, ter);
                axis.range = trange;
                axis.bounds = tbounds;
                axis.function = tfunction;
                c.time = new TemporalWCS(axis);
                c.time.timesys = timesys;
                c.time.trefpos = trefpos;
                c.time.mjdref = mjdref;
                c.time.exposure = exposure;
                c.time.resolution = tres;
            }

            // polarization
            String pctype = rs.getString(col++);
            String pcunit = rs.getString(col++);
            Double pes = Util.getDouble(rs, col++);
            Double per = Util.getDouble(rs, col++);
            CoordRange1D prange = null; // Util.decodeCoordRange1D(rs.getString(col++));
            pix1 = Util.getDouble(rs, col++);
            val1 = Util.getDouble(rs, col++);
            pix2 = Util.getDouble(rs, col++);
            val2 = Util.getDouble(rs, col++);
            if (pix1 != null)
                prange = new CoordRange1D(new RefCoord(pix1, val1), new RefCoord(pix2, val2));

            CoordBounds1D pbounds = Util.decodeCoordBounds1D(rs.getString(col++));
            CoordFunction1D pfunction = null; // Util.decodeCoordFunction1D(rs.getString(col++));
            naxis = Util.getLong(rs, col++);
            pix = Util.getDouble(rs, col++);
            val = Util.getDouble(rs, col++);
            delta = Util.getDouble(rs, col++);
            if (naxis != null)
                pfunction = new CoordFunction1D(naxis, delta, new RefCoord(pix, val));

            if (pctype != null)
            {
                CoordAxis1D axis = new CoordAxis1D(new Axis(pctype, pcunit));
                if (pes != null || per != null)
                    axis.error = new CoordError(pes, per);
                axis.range = prange;
                axis.bounds = pbounds;
                axis.function = pfunction;
                c.polarization = new PolarizationWCS(axis);
            }

            // observable
            String oda = rs.getString(col++);
            String odu = rs.getString(col++);
            Long odb = Util.getLong(rs, col++);
            String oia = rs.getString(col++);
            String oiu = rs.getString(col++);
            Long oib = Util.getLong(rs, col++);
            if (oda != null)
            {
                Slice dep = new Slice(new Axis(oda, odu), odb);
                c.observable = new ObservableAxis(dep);
                if (oia != null)
                    c.observable.independent = new Slice(new Axis(oia, oiu), oib);
            }

            Date lastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: chunk.lastModified = " + lastModified);
            Date maxLastModified = Util.getDate(rs, col++, UTC_CAL);
            log.debug("found: chunk.maxLastModified = " + dateFormat.format(maxLastModified));
            Integer stateCode = Util.getInteger(rs, col++);
            log.debug("found: chunk.stateCode = " + stateCode);
            Long id = Util.getLong(rs, col++);
            log.debug("found: chunk.id = " + id);

            Util.assignID(c, id);
            //Util.assignStateCode(c, stateCode);
            Util.assignLastModified(c, lastModified, "lastModified");
            Util.assignLastModified(c, maxLastModified, "maxLastModified");

            return c;
        }
    }
    
    private class DeletedEntityMapper implements RowMapper
    {
        private Class<? extends DeletedEntity> c;
        public DeletedEntityMapper(Class<? extends DeletedEntity> c)
        {
            this.c = c; 
        }
        
        public DeletedEntity mapRow(ResultSet rs, int row)
            throws SQLException
        {
            try
            {
                int col = 1;
                Date lastModified = Util.getDate(rs, col++, UTC_CAL);
                Long id = Util.getLong(rs, col++);
                
                Constructor<? extends DeletedEntity> ctor = c.getConstructor(Long.class, Date.class);
                DeletedEntity ret = ctor.newInstance(id, lastModified);
                log.debug("found: " + ret);
                return ret;
            }
            catch(Exception bug)
            {
                throw new RuntimeException("BUG: failed to create a " + c.getName(), bug);
            }
        }
    }
    
    class ReadAccessMapper implements RowMapper
    {
        Class<? extends ReadAccess> entityClass;
        
        ReadAccessMapper(Class<? extends ReadAccess> entityClass)
        { 
            this.entityClass = entityClass;
        }
        
        public ReadAccess mapRow(ResultSet rs, int row) throws SQLException
        {
            try
            {
                int col = 1;
                Long assetID = Util.getLong(rs, col++);
                Long groupID = Util.getLong(rs, col++);

                Constructor<? extends ReadAccess> ctor = entityClass.getConstructor(Long.class, Long.class);
                ReadAccess ret = ctor.newInstance(assetID, groupID);
                log.debug("found: " + ret);

                Date lastModified = Util.getDate(rs, col++, UTC_CAL);
                log.debug("found: ra.lastModified = " + lastModified);
                Integer stateCode = Util.getInteger(rs, col++);
                log.debug("found: ra.stateCode = " + stateCode);
                Long id = Util.getLong(rs, col++);
                log.debug("found: ra.id = " + id);

                Util.assignID(ret, id);
                //Util.assignStateCode(ret, stateCode);
                Util.assignLastModified(ret, lastModified, "lastModified");

                return ret;
            }
            catch(Exception ex)
            {
                throw new RuntimeException("BUG: failed to create a " + entityClass.getName(), ex);
            }
        }
    }

    private class SkeletonExtractor implements ResultSetExtractor
    {
        private Class<? extends Skeleton> c;
        public SkeletonExtractor(Class<? extends Skeleton> c)
        {
            this.c = c;
        }

        public Object extractData(ResultSet rs) throws SQLException, DataAccessException
        {
            if (rs.next())
                return mapRow(rs);
            return null;
        }


        private Skeleton mapRow(ResultSet rs)
            throws SQLException
        {
            try
            {
                int col = 1;
                Skeleton ret = c.newInstance();
                ret.lastModified = Util.getDate(rs, col++, UTC_CAL);
                ret.stateCode = Util.getInteger(rs, col++);
                ret.id = Util.getLong(rs, col++);
                log.debug("found: " + ret);
                return ret;
            }
            catch(Exception bug)
            {
                throw new RuntimeException("BUG: failed to create a " + c.getName(), bug);
            }
        }
    }
}