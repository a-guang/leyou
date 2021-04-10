package com.leyou.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leyou.common.pojo.PageResult;
import com.leyou.item.pojo.*;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.client.SpecificationClient;
import com.leyou.search.pojo.Goods;
import com.leyou.search.pojo.SearchRequest;
import com.leyou.search.pojo.SearchResult;
import com.leyou.search.repository.GoodsRepositry;
import com.netflix.discovery.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private CategoryClient categoryClient;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private BrandClient brandClient;

    @Autowired
    private SpecificationClient specificationClient;

    @Autowired
    private GoodsRepositry goodsRepositry;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SearchResult search(SearchRequest searchRequest) {

        if(StringUtils.isBlank(searchRequest.getKey())){
            return null;
        }
        //自定义查询构建器
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        //添加查询条件
        //1.(1)对key进行全文索引查询
//        QueryBuilder basicQuery = QueryBuilders.matchQuery("all", searchRequest.getKey()).operator(Operator.AND);
        //1.(2) 根据字段进行过滤查询
        BoolQueryBuilder basicQuery = buildBoolQueryBuilder(searchRequest);
        queryBuilder.withQuery(basicQuery);
        // 2.添加分页
        queryBuilder.withPageable(PageRequest.of(searchRequest.getPage() - 1,searchRequest.getSize()));
        // 3.添加结果集过滤
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{"id","skus","subTitle"},null));

        // 4.排序
        String sortBy = searchRequest.getSortBy();
        Boolean desc = searchRequest.getDescending();
        if(StringUtils.isNotBlank(sortBy)){
            //如果不为空,则进行排序
            queryBuilder.withSort(SortBuilders.fieldSort(sortBy).order(desc ? SortOrder.DESC :SortOrder.ASC));
        }
        //添加分类和品牌的聚合
        String categoryAggName = "categories";
        String brandAggName = "brands";
        queryBuilder.addAggregation(AggregationBuilders.terms(categoryAggName).field("cid3"));
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));

        //执行查询,获取结果集
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>)this.goodsRepositry.search(queryBuilder.build());

        //获取聚合结果集并解析
        List<Map<String,Object>> categories = getCategoryAggResult(goodsPage.getAggregation(categoryAggName));
        List<Brand> brands = getBrandAggResult(goodsPage.getAggregation(brandAggName));

        //判断是否是一个分类，只有一个分类时，才做规格参数的聚合
        List<Map<String,Object>> specs = null;
        if(!CollectionUtils.isEmpty(categories) && categories.size() == 1){
            //对规格参数进行聚合
            specs = getParamAggResult((Long)categories.get(0).get("id"),basicQuery);
        }

        return new SearchResult(goodsPage.getTotalElements(),goodsPage.getTotalPages(),goodsPage.getContent(),categories,brands,specs);
    }

    /**
     * 构建布尔查询
     * @param searchRequest
     * @return
     */
    private BoolQueryBuilder buildBoolQueryBuilder(SearchRequest searchRequest) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //给布尔查询添加基本的查询条件
        boolQueryBuilder.must(QueryBuilders.matchQuery("all",searchRequest.getKey()).operator(Operator.AND));
        //添加过滤条件
        //获取用户选择的过滤信息
        Map<String, Object> filter = searchRequest.getFilter();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            if(StringUtils.equals("品牌",key)){
                key = "brandId";
            }else if(StringUtils.equals("分类",key)){
                key = "cid3";
            }else {
                key = "specs." + key + ".keyword";
            }
            boolQueryBuilder.filter(QueryBuilders.termQuery(key,entry.getValue()));
        }

        return boolQueryBuilder;
    }

    /**
     * 根据查询条件聚合规格参数
     * @param cid
     * @param basicQuery
     * @return
     */
    private List<Map<String,Object>> getParamAggResult(Long cid, QueryBuilder basicQuery) {
        //自定义查询对象构建
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

        //添加基本查询条件
        queryBuilder.withQuery(basicQuery);

        //查询要聚合的规格参数
        List<SpecParam> params = this.specificationClient.queryParams(null, cid, null, true);

        //添加规格参数的聚合
        params.forEach(param -> {
            queryBuilder.addAggregation(AggregationBuilders.terms(param.getName()).field("specs."+param.getName()+".keyword"));
        });

        //添加结果集过滤
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{},null));

        //执行聚合查询,获取聚合结果集
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>)this.goodsRepositry.search(queryBuilder.build());

        List<Map<String,Object>> specs = new ArrayList<>();
        //解析聚合结果集, key-聚合名称(规格参数名) value-聚合对象
        Map<String,Aggregation> aggregationMap = goodsPage.getAggregations().asMap();
        for (Map.Entry<String, Aggregation> entry : aggregationMap.entrySet()) {
            //初始化一个map-{k:规格参数名  options:聚合的规格参数组}
            Map<String,Object> map = new HashMap<>();
            map.put("k",entry.getKey());
            //获取聚合
            StringTerms terms = (StringTerms) entry.getValue();

            //初始化一个options集合,收集桶中的key
            List<String> options = new ArrayList<>();
            //获取桶集合
            terms.getBuckets().forEach(bucket -> {
                options.add(bucket.getKeyAsString());
            });
            map.put("options",options);
            specs.add(map);
        }
        return specs;
    }

    /**
     * 解析品牌的聚合结果集
     * @param aggregation
     * @return
     */
    private List<Brand> getBrandAggResult(Aggregation aggregation) {
        LongTerms terms = (LongTerms) aggregation;

        //获取聚合中的桶
        return terms.getBuckets().stream().map(bucket -> this.brandClient.queryBrandById(bucket.getKeyAsNumber().longValue())).collect(Collectors.toList());


//        terms.getBuckets().forEach(bucket -> {
//            Brand brand = this.brandClient.queryBrandById(bucket.getKeyAsNumber().longValue());
//            brands.add(brand);
//        });
    }

    /**
     * 解析分类的聚合结果集
     * @param aggregation
     * @return
     */
    private List<Map<String, Object>> getCategoryAggResult(Aggregation aggregation) {
        LongTerms terms = (LongTerms) aggregation;

        //获取桶的集合，转化成List<Map<String,Object>>
        return terms.getBuckets().stream().map(bucket -> {
            //初始化一个map
            Map<String,Object> map = new HashMap<>();
            //获取桶中的分类id(key)
            long id = bucket.getKeyAsNumber().longValue();
            //根据分类id查询分类名称
            List<String> names = this.categoryClient.queryNameByIds(Arrays.asList(id));
            map.put("id",id);
            map.put("name",names.get(0));
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 构建索引库中的goods商品对象
     * @param spu
     * @return
     * @throws IOException
     */
    public Goods buildGoods(Spu spu) throws IOException {
        Goods goods = new Goods();

        //根据分类的id查询分类名称
        List<String> names = this.categoryClient.queryNameByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));

        //根据品牌id查询品牌
        Brand brand = this.brandClient.queryBrandById(spu.getBrandId());

        //根据spu的id查询所有的sku
        List<Sku> skus = this.goodsClient.querySkusBySpuId(spu.getId());
        //初始化一个价格集合,收集所有sku的价格
        List<Long> prices = new ArrayList<>();
        //收集sku的必要字段信息
        List<Map<String,Object>> skuMapList = new ArrayList<>();
        skus.forEach(sku -> {
            prices.add(sku.getPrice());

            Map<String,Object> map = new HashMap<>();
            map.put("id",sku.getId());
            map.put("title",sku.getTitle());
            map.put("price",sku.getPrice());
            //获取sku中的图片,数据库中的图片可能是多张,多张是以","分割,所以也以逗号切割,返回图片数组,获取第一张图片
            map.put("image", StringUtils.isBlank(sku.getImages()) ? "" : StringUtils.split(sku.getImages(),",")[0]);
            skuMapList.add(map);
        });

        //根据spu中的cid3查询出所有的搜索规格参数
        List<SpecParam> params = this.specificationClient.queryParams(null, spu.getCid3(), null, true);

        //根据spuId查询spuDetail
        SpuDetail spuDetail = this.goodsClient.querySpuDetailById(spu.getId());
        //把通用的规格参数值,进行反序列化
        Map<String,Object> genericSpecMap = MAPPER.readValue(spuDetail.getGenericSpec(),new TypeReference<Map<String,Object>>(){});
        //把特殊的规格参数值,进行反序列化
        Map<String,List<Object>> specialSpecMap = MAPPER.readValue(spuDetail.getSpecialSpec(),new TypeReference<Map<String,List<Object>>>(){});

        Map<String,Object> specs = new HashMap<>();
        params.forEach(param ->{
            //判断规格参数的类型,是否是通用的规格参数
            if(param.getGeneric()){
                //如果是通用类型的参数,从genericSpecMap获取规格参数
                String value = genericSpecMap.get(param.getId().toString()).toString();
                //判断是否是数值类型,如果是数值类型,应该返回一个区间
                if(param.getNumeric()){
                    value = chooseSegment(value,param);
                }
                specs.put(param.getName(),value);
            }else {
                //如果是特殊的规格参数,从specialSpecMap获取规格参数
                List<Object> value = specialSpecMap.get(param.getId().toString());
                specs.put(param.getName(),value);
            }
        });

        goods.setId(spu.getId());
        goods.setCid1(spu.getCid1());
        goods.setCid2(spu.getCid2());
        goods.setCid3(spu.getCid3());
        goods.setBrandId(spu.getBrandId());
        goods.setCreateTime(spu.getCreateTime());
        goods.setSubTitle(spu.getSubTitle());
        //拼接all字段,需要分类名称以及品牌名称
        goods.setAll(spu.getTitle() + " " + StringUtils.join(names," ") + " " + brand.getName());
        //获取spu下的所有sku的价格
        goods.setPrice(prices);
        //获取spu下的所有sku,并转化成json字符串
        goods.setSkus(MAPPER.writeValueAsString(skuMapList));
        //获取所有查询的规格参数{name:value}
        goods.setSpecs(specs);
        return goods;
    }

    private String chooseSegment(String value, SpecParam p) {
        double val = NumberUtils.toDouble(value);
        String result = "其它";
        // 保存数值段
        for (String segment : p.getSegments().split(",")) {
            String[] segs = segment.split("-");
            // 获取数值范围
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if(segs.length == 2){
                end = NumberUtils.toDouble(segs[1]);
            }
            // 判断是否在范围内
            if(val >= begin && val < end){
                if(segs.length == 1){
                    result = segs[0] + p.getUnit() + "以上";
                }else if(begin == 0){
                    result = segs[1] + p.getUnit() + "以下";
                }else{
                    result = segment + p.getUnit();
                }
                break;
            }
        }
        return result;
    }

    /**
     * 根据消息新增或修改spu信息
     * @param spuId
     * @throws IOException
     */
    public void save(Long spuId) throws IOException {
        Spu spu = this.goodsClient.querySpuById(spuId);
        Goods goods = this.buildGoods(spu);
        this.goodsRepositry.save(goods);
    }

    /**
     * 根据消息删除一个spu
     * @param spuId
     */
    public void delete(Long spuId) {
        this.goodsRepositry.deleteById(spuId);
    }
}
