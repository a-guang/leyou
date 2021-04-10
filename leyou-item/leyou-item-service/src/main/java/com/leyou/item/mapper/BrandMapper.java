package com.leyou.item.mapper;

import com.leyou.item.pojo.Brand;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface BrandMapper extends Mapper<Brand> {

    @Insert("insert into tb_category_brand values(#{cid},#{bid})")
    void insertCategoryAndBrand(@Param("cid")Long cid, @Param("bid")Long id);

    @Delete("delete from tb_category_brand where brand_id = #{bid}")
    void deleteCategoryAndBrand(Long bid);

    @Select("select * from tb_brand where id in (select brand_id from tb_category_brand where category_id = #{cid})")
    List<Brand> selectBrandsByCid(Long cid);
}
