package com.dtstack.dtcenter.loader.dto.source;

import com.dtstack.dtcenter.loader.source.DataSourceType;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * UPDRDB sourceDTO
 *
 * @author ：wangchuan
 * date：Created in 下午1:57 2021/12/6
 * company: www.dtstack.com
 */
@Data
@ToString
@SuperBuilder
public class UpDrdbSourceDTO extends Mysql5SourceDTO {

    @Override
    public Integer getSourceType() {
        return DataSourceType.UPDRDB.getVal();
    }

}
