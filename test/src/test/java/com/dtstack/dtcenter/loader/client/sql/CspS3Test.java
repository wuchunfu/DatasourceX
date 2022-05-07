package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.loader.client.BaseTest;
import com.dtstack.dtcenter.loader.client.ClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.CspS3SourceDTO;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import org.apache.commons.collections.CollectionUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * csp s3 test
 *
 * @author ：wangchuan
 * date：Created in 上午11:21 2021/12/6
 * company: www.dtstack.com
 */
public class CspS3Test extends BaseTest {

    // 获取数据源 client
    private static final IClient CLIENT = ClientCache.getClient(DataSourceType.CSP_S3.getVal());

    private static final CspS3SourceDTO SOURCE_DTO = CspS3SourceDTO.builder()
            .accessKey("AKIDwlKn4IXaU6OUN0ReX6MHySfY6KDlyqOb")
            .secretKey("zdwvay1pOecGgr76l78rHaaKq9F2cvvg")
            .region("bjf")
            .domain("cos.yun.unionpay.com")
            .build();

    @Test
    public void testCon() {
        Assert.assertTrue(CLIENT.testCon(SOURCE_DTO));
    }

    @Test
    public void listBuckets() {
        List<String> buckets = CLIENT.getAllDatabases(SOURCE_DTO, SqlQueryDTO.builder().build());
        Assert.assertTrue(CollectionUtils.isNotEmpty(buckets));
    }

    @Test
    public void listBucketsSearchLimit() {
        List<String> buckets = CLIENT.getAllDatabases(SOURCE_DTO, SqlQueryDTO.builder().schema("daishu-csp-test01-1255000139").limit(3).build());
        Assert.assertTrue(CollectionUtils.isNotEmpty(buckets));
    }

    @Test
    public void listObjectsByBucket() {
        List<String> buckets = CLIENT.getTableList(SOURCE_DTO, SqlQueryDTO.builder().schema("daishu-csp-test01-1255000139").limit(3).build());
        Assert.assertTrue(CollectionUtils.isNotEmpty(buckets));
    }
}
