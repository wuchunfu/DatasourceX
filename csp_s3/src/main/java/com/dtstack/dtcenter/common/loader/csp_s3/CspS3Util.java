package com.dtstack.dtcenter.common.loader.csp_s3;

import com.dtstack.dtcenter.loader.dto.source.CspS3SourceDTO;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * csp s3 工具类
 *
 * @author ：wangchuan
 * date：Created in 上午10:07 2021/12/6
 * company: www.dtstack.com
 */
public class CspS3Util {

    private static final Integer TIMEOUT = 60 * 1000;

    private static final String DEFAULT_DOMAIN = "cos.yun.unionpay.com";

    /**
     * 获取 csp s3 客户端
     *
     * @param sourceDTO 数据源信息
     * @return csp s3客户端
     */
    public static COSClient getClient(CspS3SourceDTO sourceDTO) {
        if (StringUtils.isBlank(sourceDTO.getRegion())) {
            throw new DtLoaderException("Region cannot be empty");
        }
        String domain = StringUtils.isBlank(sourceDTO.getDomain()) ? DEFAULT_DOMAIN : sourceDTO.getDomain();
        COSCredentials credentials = new BasicCOSCredentials(sourceDTO.getAccessKey(), sourceDTO.getSecretKey());
        SelfDefinedEndpointBuilder selfDefinedEndpointBuilder = new SelfDefinedEndpointBuilder(sourceDTO.getRegion(), domain);
        ClientConfig configuration = new ClientConfig(new Region(sourceDTO.getRegion()));
        configuration.setConnectionRequestTimeout(TIMEOUT);
        configuration.setConnectionTimeout(TIMEOUT);
        configuration.setEndpointBuilder(selfDefinedEndpointBuilder);
        configuration.setHttpProtocol(HttpProtocol.https);
        return new COSClient(credentials, configuration);
    }

    /**
     * 关闭 csp s3
     *
     * @param cosClient csp s3客户端
     */
    public static void closeAmazonS3(COSClient cosClient) {
        if (Objects.nonNull(cosClient)) {
            cosClient.shutdown();
        }
    }

    /**
     * 强转 sourceDTO 为 CspS3SourceDTO
     *
     * @param sourceDTO csp s3 sourceDTO
     * @return 转换后的 csp s3 sourceDTO
     */
    public static CspS3SourceDTO convertSourceDTO(ISourceDTO sourceDTO) {
        if (!(sourceDTO instanceof CspS3SourceDTO)) {
            throw new DtLoaderException("please pass in CspS3SourceDTO...");
        }
        return (CspS3SourceDTO) sourceDTO;
    }
}
