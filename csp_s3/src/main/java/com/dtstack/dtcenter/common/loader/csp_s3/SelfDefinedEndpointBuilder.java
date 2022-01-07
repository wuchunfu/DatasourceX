package com.dtstack.dtcenter.common.loader.csp_s3;

import com.qcloud.cos.endpoint.EndpointBuilder;
import com.qcloud.cos.region.Region;

/**
 * 自定义 Endpoint
 *
 * @author ：wangchuan
 * date：Created in 下午5:07 2021/12/6
 * company: www.dtstack.com
 */
public class SelfDefinedEndpointBuilder implements EndpointBuilder {

    private final String region;
    private final String domain;

    public SelfDefinedEndpointBuilder(String region, String domain) {
        super();
        // 格式化 Region
        this.region = Region.formatRegion(new Region(region));
        this.domain = domain;
    }

    @Override
    public String buildGeneralApiEndpoint(String bucketName) {
        // 构造 Endpoint
        String endpoint = String.format("%s.%s", this.region, this.domain);
        // 构造 Bucket 访问域名
        return String.format("%s.%s", bucketName, endpoint);
    }

    @Override
    public String buildGetServiceApiEndpoint() {
        return String.format("%s.%s", this.region, this.domain);
    }
}
