package com.leyou.item.service;

import com.leyou.item.mapper.SpecGroupMapper;
import com.leyou.item.mapper.SpecParamMapper;
import com.leyou.item.pojo.SpecGroup;
import com.leyou.item.pojo.SpecParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpecificationService {

    @Autowired
    private SpecGroupMapper specGroupMapper;
    @Autowired
    private SpecParamMapper specParamMapper;

    /**
     * 根据分类id查询参数组
     * @param cid
     * @return
     */
    public List<SpecGroup> queryGroupByCid(Long cid) {
        SpecGroup record = new SpecGroup();
        record.setCid(cid);
        return this.specGroupMapper.select(record);
    }


    /**
     * 根据条件查询规格参数
     *
     * @param gid
     * @param cid
     * @param generic
     * @param searching
     * @return
     */
    public List<SpecParam> queryParams(Long gid, Long cid,Boolean generic, Boolean searching) {
        SpecParam record = new SpecParam();
        record.setGroupId(gid);
        record.setCid(cid);
        record.setGeneric(generic);
        record.setSearching(searching);
        return this.specParamMapper.select(record);
    }

    /**
     * 新增参数或者修改参数名的属性(是否通用,为数值,可搜索等)
     * @param specParam
     */
    public void insertOrUpdateParam(SpecParam specParam) {
        if(specParam.getId() == null){
            this.specParamMapper.insert(specParam);
        }else {
            this.specParamMapper.updateByPrimaryKey(specParam);
        }
    }

    /**
     * 根据参数id删除参数
     * @param pid
     * @return
     */
    public void deleteParam(Long pid) {
        this.specParamMapper.deleteByPrimaryKey(pid);
    }

    /**
     * 根据cid查询其所有组的参数
     * @param cid
     * @return
     */
    public List<SpecGroup> queryGroupWithParam(Long cid) {
        List<SpecGroup> groups = this.queryGroupByCid(cid);
        groups.forEach(group -> {
            List<SpecParam> params = this.queryParams(group.getId(), null, null, null);
            group.setParams(params);
        });
        return groups;
    }
}
