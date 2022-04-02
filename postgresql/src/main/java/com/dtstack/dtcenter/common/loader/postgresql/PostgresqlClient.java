package com.dtstack.dtcenter.common.loader.postgresql;

import com.dtstack.dtcenter.common.loader.common.utils.DBUtil;
import com.dtstack.dtcenter.common.loader.rdbms.AbsRdbmsClient;
import com.dtstack.dtcenter.common.loader.rdbms.ConnFactory;
import com.dtstack.dtcenter.loader.IDownloader;
import com.dtstack.dtcenter.loader.dto.ColumnMetaDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.dto.source.PostgresqlSourceDTO;
import com.dtstack.dtcenter.loader.dto.source.RdbmsSourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 15:52 2020/1/7
 * @Description：Postgresql 客户端
 */
@Slf4j
public class PostgresqlClient extends AbsRdbmsClient {
    private static final String SMALLSERIAL = "smallserial";

    private static final String SERIAL = "serial";

    private static final String BIGSERIAL = "bigserial";

    private static final String DATABASE_QUERY = "select nspname from pg_namespace";

    private static final String DONT_EXIST = "doesn't exist";

    // 获取指定schema下的表，包括视图
    private static final String SHOW_TABLE_AND_VIEW_BY_SCHEMA_SQL = "SELECT table_name FROM information_schema.tables WHERE table_schema = '%s' %s";

    // 获取指定schema下的表，不包括视图
    private static final String SHOW_TABLE_BY_SCHEMA_SQL = "SELECT table_name FROM information_schema.tables WHERE table_schema = '%s' AND table_type = 'BASE TABLE' %s";

    //获取所有表名，包括视图，表名前拼接schema，并对schema和tableName进行增加双引号处理
    private static final String ALL_TABLE_AND_VIEW_SQL = "SELECT '\"'||table_schema||'\".\"'||table_name||'\"' AS schema_table FROM information_schema.tables WHERE 1 = 1 %s order by schema_table ";

    //获取所有表名，不包括视图，表名前拼接schema，并对schema和tableName进行增加双引号处理
    private static final String ALL_TABLE_SQL = "SELECT '\"'||table_schema||'\".\"'||table_name||'\"' AS schema_table FROM information_schema.tables WHERE table_type = 'BASE TABLE' %s order by schema_table ";

    // 获取正在使用数据库
    private static final String CURRENT_DB = "select current_database()";

    // 根据schema选表表名模糊查询
    private static final String SEARCH_SQL = " AND table_name LIKE '%s' ";

    // 限制条数语句
    private static final String LIMIT_SQL = " LIMIT %s ";

    // 获取当前版本号
    private static final String SHOW_VERSION = "show server_version";

    // 创建 schema
    private static final String CREATE_SCHEMA_SQL_TMPL = "create schema %s ";

    // 查询表注释
    private static final String TABLE_COMMENT = "select relname as tabname, cast(obj_description(oid,'pg_class') as varchar) as comment from pg_class c where relname = '%s' %s";

    // 查询字段注释
    private static final String COLUMN_COMMENT = "SELECT A.attname AS column,D.description AS comment FROM pg_class C,pg_attribute A,pg_description D WHERE C.relname = '%s' %s AND A.attnum > 0 AND A.attrelid = C.oid AND D.objoid = A.attrelid AND D.objsubid = A.attnum";

    // 查询 schema 的 oid（主键）
    private static final String SCHEMA_RECORD_OID = " and relnamespace in (select oid from pg_namespace where nspname = '%s')";

    // 获取指定 schema 下面表的字段信息
    private static final String  SHOW_TABLE_COLUMN_BY_SCHEMA = "SELECT array_to_string(ARRAY(select concat( c1, c2, c3, c4) as column_line from (select column_name || ' ' || data_type as c1,case when character_maximum_length > 0 then '(' || character_maximum_length || ')' end as c2,case when is_nullable = 'NO' then ' NOT NULL' end as c3,case when column_default is not Null then ' DEFAULT' end || ' ' || replace(column_default, '::character varying', '') as c4 from information_schema.columns where table_name = '%1$s' and table_schema='%2$s' order by ordinal_position) as string_columns), ',') as column";

    // 获取指定表的字段信息
    private static final String SHOW_TABLE_COLUMN = "SELECT array_to_string(ARRAY(select concat( c1, c2, c3, c4) as column_line from (select column_name || ' ' || data_type as c1,case when character_maximum_length > 0 then '(' || character_maximum_length || ')' end as c2,case when is_nullable = 'NO' then ' NOT NULL' end as c3,case when column_default is not Null then ' DEFAULT' end || ' ' || replace(column_default, '::character varying', '') as c4 from information_schema.columns where table_name = '%1$s' order by ordinal_position) as string_columns), ',') as column";

    // 获取指定 schema 下面表的约束
    private static final String SHOW_TABLE_CONSTRAINT_BY_SCHEMA = "select array_to_string(\n" +
            "array(\n" +
            "select concat(' CONSTRAINT ',conname ,c,u,p,f)   from (\n" +
            "select conname,\n" +
            "case when contype='c' then ' CHECK('|| consrc ||')' end  as c  ,\n" +
            "case when contype='u' then ' UNIQUE('|| ( SELECT array_to_string(ARRAY (SELECT A.attname FROM pg_attribute A WHERE A.attrelid = (select oid from pg_class where relname= '%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s')) AND A.attnum IN ( SELECT UNNEST ( conkey ) FROM pg_constraint C WHERE contype = 'u' AND C.conrelid = ((select oid from pg_class where relname= '%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s'))) AND ( array_to_string( conkey, ',' ) IS NOT NULL))),',') ) ||')' end as u ,\n" +
            "case when contype='p' then ' PRIMARY KEY ('|| ( SELECT array_to_string(ARRAY (SELECT A.attname FROM pg_attribute A WHERE A.attrelid = (select oid from pg_class where relname= '%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s')) AND A.attnum IN ( SELECT UNNEST ( conkey ) FROM pg_constraint C WHERE contype = 'p' AND C.conrelid = ((select oid from pg_class where relname= '%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s'))) AND ( array_to_string( conkey, ',' ) IS NOT NULL))),',')) ||')' end  as p  ,\n" +
            "case when contype='f' then ' FOREIGN KEY('|| ( SELECT array_to_string(ARRAY (SELECT A.attname FROM pg_attribute A WHERE A.attrelid = (select oid from pg_class where relname= '%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s')) AND A.attnum IN ( SELECT UNNEST ( conkey ) FROM pg_constraint C WHERE contype = 'u' AND C.conrelid = ((select oid from pg_class where relname= '%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s'))) AND ( array_to_string( conkey, ',' ) IS NOT NULL))),',')) ||') REFERENCES '|| \n" +
            "(select p.relname from pg_class p where p.oid=c.confrelid )  || '('|| ( SELECT array_to_string(ARRAY (SELECT A.attname FROM pg_attribute A WHERE A.attrelid = ((select oid from pg_class where relname= '%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s'))) AND A.attnum IN ( SELECT UNNEST ( conkey ) FROM pg_constraint C WHERE contype = 'u' AND C.conrelid = (select oid from pg_class where relname= '%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s')) AND ( array_to_string( conkey, ',' ) IS NOT NULL))),',') ) ||')' end as  f\n" +
            "from pg_constraint c\n" +
            "where contype in('u','c','f','p') and conrelid=( \n" +
            "select oid  from pg_class  where relname='%1$s' and relnamespace = (select oid from pg_namespace where nspname = '%2$s') \n" +
            " )\n" +
            ") as t  \n" +
            ") ,',' ) as constraint";

    // 获取指定 schema 下面表的约束
    private static final String SHOW_TABLE_CONSTRAINT = "select array_to_string(\n" +
            "array(\n" +
            "select concat(' CONSTRAINT ',conname ,c,u,p,f)   from (\n" +
            "select conname,\n" +
            "case when contype='c' then ' CHECK('|| consrc ||')' end  as c  ,\n" +
            "case when contype='u' then ' UNIQUE('|| ( SELECT array_to_string(ARRAY (SELECT A.attname FROM pg_attribute A WHERE A.attrelid = (select oid from pg_class where relname= '%1$s' ) AND A.attnum IN ( SELECT UNNEST ( conkey ) FROM pg_constraint C WHERE contype = 'u' AND C.conrelid = ((select oid from pg_class where relname= '%1$s' )) AND ( array_to_string( conkey, ',' ) IS NOT NULL))),',') ) ||')' end as u ,\n" +
            "case when contype='p' then ' PRIMARY KEY ('|| ( SELECT array_to_string(ARRAY (SELECT A.attname FROM pg_attribute A WHERE A.attrelid = (select oid from pg_class where relname= '%1$s' ) AND A.attnum IN ( SELECT UNNEST ( conkey ) FROM pg_constraint C WHERE contype = 'p' AND C.conrelid = ((select oid from pg_class where relname= '%1$s' )) AND ( array_to_string( conkey, ',' ) IS NOT NULL))),',')) ||')' end  as p  ,\n" +
            "case when contype='f' then ' FOREIGN KEY('|| ( SELECT array_to_string(ARRAY (SELECT A.attname FROM pg_attribute A WHERE A.attrelid = (select oid from pg_class where relname= '%1$s' ) AND A.attnum IN ( SELECT UNNEST ( conkey ) FROM pg_constraint C WHERE contype = 'u' AND C.conrelid = ((select oid from pg_class where relname= '%1$s' )) AND ( array_to_string( conkey, ',' ) IS NOT NULL))),',')) ||') REFERENCES '|| \n" +
            "(select p.relname from pg_class p where p.oid=c.confrelid )  || '('|| ( SELECT array_to_string(ARRAY (SELECT A.attname FROM pg_attribute A WHERE A.attrelid = ((select oid from pg_class where relname= '%1$s' )) AND A.attnum IN ( SELECT UNNEST ( conkey ) FROM pg_constraint C WHERE contype = 'u' AND C.conrelid = (select oid from pg_class where relname= '%1$s' ) AND ( array_to_string( conkey, ',' ) IS NOT NULL))),',') ) ||')' end as  f\n" +
            "from pg_constraint c\n" +
            "where contype in('u','c','f','p') and conrelid=( \n" +
            "select oid  from pg_class  where relname='%1$s' \n" +
            " )\n" +
            ") as t  \n" +
            ") ,',' ) as constraint";

    // 格式刷 schema 和 表名
    private static final String SCHEMA_TABLE_FORMAT = "\"%s\".\"%s\"";

    // 建表模版
    private static final String CREATE_TABLE_TEMPLATE = "CREATE TABLE %s (%s);";

    @Override
    protected ConnFactory getConnFactory() {
        return new PostgresqlConnFactory();
    }

    @Override
    protected DataSourceType getSourceType() {
        return DataSourceType.PostgreSQL;
    }

    @Override
    public List<String> getTableList(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        PostgresqlSourceDTO postgresqlSourceDTO = (PostgresqlSourceDTO) iSource;
        Integer clearStatus = beforeQuery(postgresqlSourceDTO, queryDTO, false);
        Statement statement = null;
        ResultSet rs = null;
        try {
            statement = postgresqlSourceDTO.getConnection().createStatement();
            if (Objects.nonNull(queryDTO) && Objects.nonNull(queryDTO.getLimit())) {
                // 设置最大条数
                statement.setMaxRows(queryDTO.getLimit());
            }
            StringBuilder constr = new StringBuilder();
            if (Objects.nonNull(queryDTO) && StringUtils.isNotBlank(queryDTO.getTableNamePattern())) {
                constr.append(String.format(SEARCH_SQL, addFuzzySign(queryDTO)));
            }
            //大小写区分，不传schema默认获取所有表，并且表名签名拼接schema，格式："schema"."tableName"
            String schema = StringUtils.isNotBlank(queryDTO.getSchema()) ? queryDTO.getSchema() : postgresqlSourceDTO.getSchema();
            String querySql;
            if (StringUtils.isNotBlank(schema)) {
                querySql = queryDTO.getView() ? String.format(SHOW_TABLE_AND_VIEW_BY_SCHEMA_SQL, schema, constr.toString()) : String.format(SHOW_TABLE_BY_SCHEMA_SQL, schema, constr.toString());
            }else {
                querySql = queryDTO.getView() ? String.format(ALL_TABLE_AND_VIEW_SQL, constr.toString()) : String.format(ALL_TABLE_SQL, constr.toString());
            }
            DBUtil.setFetchSize(statement, queryDTO);
            rs = statement.executeQuery(querySql);
            List<String> tableList = new ArrayList<>();
            while (rs.next()) {
                tableList.add(rs.getString(1));
            }
            return tableList;
        } catch (Exception e) {
            throw new DtLoaderException(String.format("get table exception,%s", e.getMessage()), e);
        } finally {
            DBUtil.closeDBResources(rs, statement, DBUtil.clearAfterGetConnection(postgresqlSourceDTO, clearStatus));
        }
    }

    @Override
    public String getTableMetaComment(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        PostgresqlSourceDTO postgresqlSourceDTO = (PostgresqlSourceDTO) iSource;
        Integer clearStatus = beforeColumnQuery(postgresqlSourceDTO, queryDTO);

        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = postgresqlSourceDTO.getConnection().createStatement();
            String schema = StringUtils.isNotBlank(queryDTO.getSchema()) ? queryDTO.getSchema() : postgresqlSourceDTO.getSchema();
            String schemaOidSql = StringUtils.isNotBlank(schema) ? String.format(SCHEMA_RECORD_OID, schema) : "";
            resultSet = statement.executeQuery(String.format(TABLE_COMMENT, queryDTO.getTableName(), schemaOidSql));
            while (resultSet.next()) {
                String dbTableName = resultSet.getString(1);
                if (dbTableName.equalsIgnoreCase(queryDTO.getTableName())) {
                    return resultSet.getString(2);
                }
            }
        } catch (Exception e) {
            throw new DtLoaderException(String.format("get table: %s's information error. Please contact the DBA to check the database、table information.",
                    queryDTO.getTableName()), e);
        } finally {
            DBUtil.closeDBResources(resultSet, statement, DBUtil.clearAfterGetConnection(postgresqlSourceDTO, clearStatus));
        }
        return "";
    }

    @Override
    protected Map<String, String> getColumnComments(RdbmsSourceDTO sourceDTO, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeQuery(sourceDTO, SqlQueryDTO.builder().build(), false);
        String schema = StringUtils.isNotBlank(queryDTO.getSchema()) ? queryDTO.getSchema() : sourceDTO.getSchema();
        String columnCommentSql = StringUtils.isNoneBlank(schema) ? String.format(COLUMN_COMMENT, queryDTO.getTableName(), String.format(SCHEMA_RECORD_OID, schema)) : String.format(COLUMN_COMMENT, queryDTO.getTableName(), "");
        Map<String, String> comments = Maps.newHashMap();
        try {
            List<Map<String, Object>> result = executeQuery(sourceDTO, SqlQueryDTO.builder().sql(columnCommentSql).build());
            for (Map<String, Object> row : result) {
                comments.put(MapUtils.getString(row, "column"), MapUtils.getString(row, "comment"));
            }
        } finally {
            DBUtil.clearAfterGetConnection(sourceDTO, clearStatus);
        }
        return comments;
    }

    @Override
    protected String doDealType(ResultSetMetaData rsMetaData, Integer los) throws SQLException {
        String type = super.doDealType(rsMetaData, los);

        // smallserial、serial、bigserial 需要转换
        if (SMALLSERIAL.equalsIgnoreCase(type)) {
            return "int2";
        }
        if (SERIAL.equalsIgnoreCase(type)) {
            return "int4";
        }
        if (BIGSERIAL.equalsIgnoreCase(type)) {
            return "int8";
        }

        return type;
    }

    @Override
    public IDownloader getDownloader(ISourceDTO source, SqlQueryDTO queryDTO) throws Exception {
        PostgresqlSourceDTO postgresqlSourceDTO = (PostgresqlSourceDTO) source;
        String schema = StringUtils.isNotBlank(queryDTO.getSchema()) ? queryDTO.getSchema() : postgresqlSourceDTO.getSchema();
        PostgresqlDownloader postgresqlDownloader = new PostgresqlDownloader(getCon(postgresqlSourceDTO), queryDTO.getSql(), schema);
        postgresqlDownloader.configure();
        return postgresqlDownloader;
    }

    @Override
    public List<ColumnMetaDTO> getFlinkColumnMetaData(ISourceDTO source, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeColumnQuery(source, queryDTO);
        PostgresqlSourceDTO postgresqlSourceDTO = (PostgresqlSourceDTO) source;
        Statement statement = null;
        ResultSet rs = null;
        List<ColumnMetaDTO> columns = new ArrayList<>();
        try {
            statement = postgresqlSourceDTO.getConnection().createStatement();
            String queryColumnSql = "select * from " + transferSchemaAndTableName(postgresqlSourceDTO, queryDTO)
                    + " where 1=2";
            rs = statement.executeQuery(queryColumnSql);
            ResultSetMetaData rsMetaData = rs.getMetaData();
            for (int i = 0, len = rsMetaData.getColumnCount(); i < len; i++) {
                ColumnMetaDTO columnMetaDTO = new ColumnMetaDTO();
                columnMetaDTO.setKey(rsMetaData.getColumnName(i + 1));
                String type = rsMetaData.getColumnTypeName(i + 1);
                int columnType = rsMetaData.getColumnType(i + 1);
                int precision = rsMetaData.getPrecision(i + 1);
                int scale = rsMetaData.getScale(i + 1);
                //postgresql类型转换
                String flinkSqlType = PostgreSqlAdapter.mapColumnTypeJdbc2Java(columnType, precision, scale);
                if (StringUtils.isNotEmpty(flinkSqlType)) {
                    type = flinkSqlType;
                }
                columnMetaDTO.setType(type);
                // 获取字段精度
                if (columnMetaDTO.getType().equalsIgnoreCase("decimal")
                        || columnMetaDTO.getType().equalsIgnoreCase("float")
                        || columnMetaDTO.getType().equalsIgnoreCase("double")
                        || columnMetaDTO.getType().equalsIgnoreCase("numeric")) {
                    columnMetaDTO.setScale(rsMetaData.getScale(i + 1));
                    columnMetaDTO.setPrecision(rsMetaData.getPrecision(i + 1));
                }
                columns.add(columnMetaDTO);
            }
            return columns;

        } catch (SQLException e) {
            if (e.getMessage().contains(DONT_EXIST)) {
                throw new DtLoaderException(String.format(queryDTO.getTableName() + "table not exist,%s", e.getMessage()), e);
            } else {
                throw new DtLoaderException(String.format("Failed to get meta information for the fields of table :%s. Please contact the DBA to check the database table information.", queryDTO.getTableName()), e);
            }
        } finally {
            DBUtil.closeDBResources(rs, statement, DBUtil.clearAfterGetConnection(postgresqlSourceDTO, clearStatus));
        }

    }

    @Override
    public String getCreateTableSql(ISourceDTO source, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeQuery(source, queryDTO, false);
        PostgresqlSourceDTO postgresqlSourceDTO = (PostgresqlSourceDTO) source;
        String schema = StringUtils.isNotBlank(queryDTO.getSchema()) ? queryDTO.getSchema() : postgresqlSourceDTO.getSchema();
        List<Map<String, Object>> columnResult;
        List<Map<String, Object>> constraintResult;
        String tableName;
        if (StringUtils.isNotBlank(schema)) {
            try {
                columnResult = executeQuery(postgresqlSourceDTO, SqlQueryDTO.builder().sql(String.format(SHOW_TABLE_COLUMN_BY_SCHEMA, queryDTO.getTableName(), schema)).build());
                constraintResult = executeQuery(postgresqlSourceDTO, SqlQueryDTO.builder().sql(String.format(SHOW_TABLE_CONSTRAINT_BY_SCHEMA, queryDTO.getTableName(), schema)).build());
                tableName = String.format(SCHEMA_TABLE_FORMAT, schema, queryDTO.getTableName());
            } finally {
                DBUtil.closeDBResources(null, null, DBUtil.clearAfterGetConnection(postgresqlSourceDTO, clearStatus));
            }
        } else {
            try {
                columnResult = executeQuery(postgresqlSourceDTO, SqlQueryDTO.builder().sql(String.format(SHOW_TABLE_COLUMN, queryDTO.getTableName())).build());
                constraintResult = executeQuery(postgresqlSourceDTO, SqlQueryDTO.builder().sql(String.format(SHOW_TABLE_CONSTRAINT, queryDTO.getTableName())).build());
                tableName = queryDTO.getTableName();
            } finally {
                DBUtil.closeDBResources(null, null, DBUtil.clearAfterGetConnection(postgresqlSourceDTO, clearStatus));
            }
        }
        if (CollectionUtils.isEmpty(columnResult) || StringUtils.isBlank(MapUtils.getString(columnResult.get(0), "column"))) {
            throw new DtLoaderException(String.format("Failed to get table %s field", queryDTO.getTableName()));
        }
        String columnStr = MapUtils.getString(columnResult.get(0), "column");
        String constraint = null;
        if (CollectionUtils.isNotEmpty(constraintResult)) {
            constraint = MapUtils.getString(constraintResult.get(0), "constraint");
        }
        if (StringUtils.isNotBlank(constraint)) {
            return String.format(CREATE_TABLE_TEMPLATE, tableName, columnStr + " , " +constraint);
        }
        return String.format(CREATE_TABLE_TEMPLATE, tableName, columnStr);
    }

    @Override
    public List<ColumnMetaDTO> getPartitionColumn(ISourceDTO source, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public String getShowDbSql() {
        return DATABASE_QUERY;
    }

    @Override
    protected String getTableBySchemaSql(ISourceDTO sourceDTO, SqlQueryDTO queryDTO) {
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) sourceDTO;
        String schema = StringUtils.isNotBlank(queryDTO.getSchema()) ? queryDTO.getSchema() : rdbmsSourceDTO.getSchema();
        // 如果不传scheme，默认使用当前连接使用的schema
        if (StringUtils.isBlank(schema)) {
            throw new DtLoaderException("schema is not empty...");
        }
        log.info("current used schema：{}", schema);
        StringBuilder constr = new StringBuilder();
        if (StringUtils.isNotBlank(queryDTO.getTableNamePattern())) {
            constr.append(String.format(SEARCH_SQL, addFuzzySign(queryDTO)));
        }
        if (Objects.nonNull(queryDTO.getLimit())) {
            constr.append(String.format(LIMIT_SQL, queryDTO.getLimit()));
        }
        return String.format(SHOW_TABLE_BY_SCHEMA_SQL, schema, constr.toString());
    }

    /**
     * 处理Postgresql schema和tableName，适配schema和tableName中有.的情况
     * @param schema
     * @param tableName
     * @return
     */
    @Override
    protected String transferSchemaAndTableName(String schema, String tableName) {
        if (StringUtils.isBlank(schema)) {
            return tableName;
        }
        return String.format("%s.\"%s\"", schema, tableName);
    }

    @Override
    protected String getCurrentDbSql() {
        return CURRENT_DB;
    }

    @Override
    protected String getVersionSql() {
        return SHOW_VERSION;
    }

    @Override
    protected String getCreateDatabaseSql(String dbName, String comment) {
        return String.format(CREATE_SCHEMA_SQL_TMPL, dbName);
    }
}
