package com.leyou.item.controller;

import com.leyou.item.pojo.SpecGroup;
import com.leyou.item.pojo.SpecParam;
import com.leyou.item.service.SpecificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("spec")
public class SpecificationController {

    @Autowired
    private SpecificationService specificationService;

    /**
     * 根据分类id查询参数组
     * @param cid
     * @return
     */
    @GetMapping("groups/{cid}")
    public ResponseEntity<List<SpecGroup>> queryGroupsByCid(@PathVariable("cid")Long cid){
        List<SpecGroup> groups = this.specificationService.queryGroupByCid(cid);
        if(CollectionUtils.isEmpty(groups)){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(groups);
    }

    /**
     * 根据条件查询规格参数
     * @param gid
     * @return
     */
    @GetMapping("params")
    public ResponseEntity<List<SpecParam>> queryParams(
            @RequestParam(value = "gid",required = false)Long gid,
            @RequestParam(value = "cid",required = false)Long cid,
            @RequestParam(value = "generic",required = false)Boolean generic,
            @RequestParam(value = "searching",required = false)Boolean searching
    ){
        List<SpecParam> params = this.specificationService.queryParams(gid,cid,generic,searching);
        if(CollectionUtils.isEmpty(params)){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(params);
    }

    /**
     * 修改参数名的属性(是否通用,为数值,可搜索等)
     * @param specParam
     * @return
     */
    @RequestMapping("param")
    public ResponseEntity<Void> insertOrUpdateParam( @RequestBody SpecParam specParam){
        this.specificationService.insertOrUpdateParam(specParam);
        return ResponseEntity.status(HttpStatus.CREATED).build(); //如果不返回HttpStatus.CREATED会报500错误
    }

    /**
     * 根据参数id删除参数
     * @param pid
     * @return
     */
    @DeleteMapping("param/{pid}")
    public ResponseEntity<Void> deleteParam(@PathVariable("pid")Long pid){
        this.specificationService.deleteParam(pid);
        return ResponseEntity.ok().build();
    }

    /**
     * 根据cid查询其所有组的参数
     * @param cid
     * @return
     */
    @GetMapping("group/param/{cid}")
    public ResponseEntity<List<SpecGroup>> queryGroupWithParam(@PathVariable("cid")Long cid){
        List<SpecGroup> groups = this.specificationService.queryGroupWithParam(cid);
        if(CollectionUtils.isEmpty(groups)){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(groups);
    }
}
