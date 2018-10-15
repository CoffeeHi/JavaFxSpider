package com.cx.coffeehi.spider.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.cx.coffeehi.spider.bean.Author;
import com.cx.coffeehi.spider.bean.Data;
import com.cx.coffeehi.spider.bean.Paging;
import com.cx.coffeehi.spider.bean.Result;
import com.cx.coffeehi.spider.bean.SpiderContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.extern.log4j.Log4j;

@Log4j
@lombok.Data
public class SpiderUtils {
    private static String zhUrlPrefix = "https://www.zhihu.com/api/v4/questions/id/answers";
    public static AtomicLong nowNum = new AtomicLong(0);
    public static AtomicLong totalNum = new AtomicLong(0);

    private class GetAnswer implements Runnable {
        private SpiderContext spiderContext;
        private Data data;

        public GetAnswer(SpiderContext spiderContext, Data data) {
            this.spiderContext = spiderContext;
            this.data = data;
        }

        @Override
        public void run() {
            log.info("Answers Num :" + nowNum.incrementAndGet());
            if (data.getVoteup_count() < 10) {
                return;
            }
            Author author = data.getAuthor();
            String content = data.getContent();
            Document doc = Jsoup.parse(content);
            Elements pics = doc.select("img[src~=(?i).(png|jpe?g)]");
            int picIndex = 1;
            boolean isAnonymous = "匿名用户".equals(author.getName());
            for (Element pic : pics) {
                String picUrl = pic.attr("data-original");
                if (StringUtils.isEmpty(picUrl)) {
                    picUrl = pic.attr("src");
                    String picClass = pic.attr("class");
                    if (StringUtils.isEmpty(picUrl) || !"thumbnail".equals(picClass)) {
                        continue;
                    }
                }
//                log.info("picUrl: " + picUrl);
                String suffix = picUrl.substring(picUrl.lastIndexOf("."));
                String saveDir = spiderContext.getSavePath();
                String anonymousName = "匿名-" + data.getId() + "-" + (picIndex++) + suffix;
                String normalName = author.getName() + "-" + data.getId() + "-" + (picIndex++) + suffix;
                String picName = isAnonymous ? anonymousName : normalName;
                SpiderThread.getInstance().picTaskSubmit(new GetPics(picUrl, saveDir, picName));
            }
        }
    }

    private class GetPics implements Runnable {

        private String picUrl;
        private String saveDir;
        private String picName;

        public GetPics(String picUrl, String saveDir, String picName) {
            this.picUrl = picUrl;
            this.saveDir = saveDir;
            this.picName = picName;
        }

        @Override
        public void run() {
            try {
                downloadImg(picUrl, saveDir, picName);
            } catch (Exception e) {
                log.error(e);
            }
        }

    }

    public static void spiderGo(SpiderContext spiderContext) throws ParseException, IOException, InterruptedException {
        int limit = 20;
        int page = 0;
        while (SpiderContext.isRunning) {
            int offset = page * limit;
            log.info("page: " + page + "; " + "limit: " + limit + ";" + "offset: " + offset);
            Map<String, String> paramMap = Maps.newHashMap();
            paramMap.put("include",
                "data[*].is_normal,admin_closed_comment,reward_info,is_collapsed,annotation_action,annotation_detail,collapse_reason,is_sticky,collapsed_by,suggest_edit,comment_count,can_comment,content,editable_content,voteup_count,reshipment_settings,comment_permission,created_time,updated_time,review_info,relevant_info,question,excerpt,relationship.is_authorized,is_author,voting,is_thanked,is_nothelp;data[*].mark_infos[*].url;data[*].author.follower_count,badge[*].topics");
            paramMap.put("limit", String.valueOf(limit));
            paramMap.put("offset", String.valueOf(offset));
            paramMap.put("sort_by", "default");
            page++;
            String result = doGet(zhUrlPrefix.replace("id", spiderContext.getQuestionId()), paramMap);
            Result resultObj = JSON.parseObject(result, new TypeReference<Result>() {});
            Paging paging = resultObj.getPaging();
            Long totals = paging.getTotals();
            totalNum.set(totals);
            List<Data> datas = resultObj.getData();
            for (final Data data : datas) {
                SpiderThread.getInstance().ansTaskSubmit(new SpiderUtils().new GetAnswer(spiderContext, data));
            }
            if (paging.getIs_end() && offset + limit > totals) {
                break;
            }
        }
    }

    public static String doGet(String url, Map<String, String> map)
        throws ParseException, IOException, InterruptedException {
        CloseableHttpClient httpClient = null;
        HttpGet httpGet = null;
        String result = null;
        CloseableHttpResponse response = null;
        CookieStore cookieStore = new BasicCookieStore();
        List<BasicClientCookie> cookies =
            Lists.newArrayList(new BasicClientCookie("_xsrf", "iNEbyq5XRswkM3S8u7bkAY4iHkPLqz70"),
                new BasicClientCookie("_zap", "iNEbyq5XRswkM3S8u7bkAY4iHkPLqz70"),
                new BasicClientCookie("d_c0", "\"AAAotaVyWA6PTheLYUy_KuTRJ9U_RwAH7yM=|1539233802\""),
                new BasicClientCookie("q_c1", "545aef250abc4b249f16db096845d7f4|1539233802000|1539233802000"),
                new BasicClientCookie("capsion_ticket",
                    "\"2|1:0|10:1539234096|14:capsion_ticket|44:NGRlMjIxNDVhYWI3NGY0Y2FiZDBiODVlOWNjNDg2MzU=|6999604e4eaa44d553d8fc7e22ab321a9861bff8b87bb3490a23e90e3a9590ae\""),
                new BasicClientCookie("z_c0",
                    "\"2|1:0|10:1539234202|4:z_c0|92:Mi4xbDlyN0FRQUFBQUFBQUNpMXBYSllEaVlBQUFCZ0FsVk5taWVzWEFENGYzTkVrT3JnbVpLSHhSakRmeHBwZmh0bldB|f499c13e473df36f1f8e5fe8260e7642fed9e177ea6aee1138f4ee7628622714\""),
                new BasicClientCookie("tgw_l7_route", "61066e97b5b7b3b0daad1bff47134a22"));
        for (BasicClientCookie cookie : cookies) {
            // cookieStore.addCookie(cookie);
        }
        // 构造Headers
        List<Header> headerList = Lists.newArrayList();
        // headerList.add(new BasicHeader(HttpHeaders.ACCEPT_ENCODING,"gzip, deflate, br"));
        headerList.add(new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9"));
        headerList.add(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        headerList.add(new BasicHeader("x-udid", "AAAotaVyWA6PTheLYUy_KuTRJ9U_RwAH7yM="));
        headerList.add(new BasicHeader("x-requested-with", "fetch"));
        headerList.add(new BasicHeader("content-type", "application/json"));
        // headerList.add(new BasicHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 6.1; WOW64)
        // AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"));

        httpClient = HttpClients.custom().setDefaultHeaders(headerList).setDefaultCookieStore(cookieStore).build();
        httpGet = new HttpGet(url);
        List<NameValuePair> list = Lists.newArrayList();
        Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, String> elem = iterator.next();
            list.add(new BasicNameValuePair(elem.getKey(), elem.getValue()));
        }
        if (list.size() > 0) {
            String str = EntityUtils.toString(new UrlEncodedFormEntity(list, Consts.UTF_8));
            url = url + "?" + str;
            httpGet = new HttpGet(url);
        }
        while (response == null) {
            try {
                response = httpClient.execute(httpGet);
            } catch (Exception e) {
                Thread.currentThread().sleep(500);
                log.error("HTTP GET ERROR : ", e);
            }
        }
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            log.info("HTTP STATUS : " + response.getStatusLine());
            HttpEntity entity = response.getEntity();
            Header contentEncoding = entity.getContentEncoding();
            if (contentEncoding != null) {
                String encode = contentEncoding.getValue();
                if ("gzip".equalsIgnoreCase(encode)) {
                    entity = new GzipDecompressingEntity(entity);
                } else if ("deflate".equalsIgnoreCase(encode)) {
                    entity = new DeflateDecompressingEntity(entity);
                } else if ("br".equalsIgnoreCase(encode)) {
                    log.info("fuck br");
                }
            }
            result = EntityUtils.toString(entity, Consts.UTF_8);// 获得返回的结果
        }
        return result;
    }

    // picUrl 图片连接，name 图片名称，imgPath 图片要保存的地址
    public static void downloadImg(String picUrl, String saveDir, String imgName)
        throws ClientProtocolException, IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet get = new HttpGet(picUrl);
            HttpResponse response = httpclient.execute(get);
            HttpEntity entity = response.getEntity();
            InputStream in = entity.getContent();
            String absolutePath = saveDir + "\\" + imgName;
            log.info("FilePath: " + absolutePath);
            File file = new File(absolutePath);
            try {
                if (!file.exists()) {
                    file.createNewFile();
                } else {
                    log.info("contentLength: " + entity.getContentLength());
                    log.info("fileName: " + file.getName() + "fileExistedLength: " + file.length());
                    if (entity.getContentLength() == file.length()) {
                        return;
                    }
                }
                FileOutputStream fout = new FileOutputStream(file);
                int l = -1;
                byte[] tmp = new byte[1024];
                while ((l = in.read(tmp)) != -1) {
                    fout.write(tmp, 0, l);
                }
                fout.flush();
                fout.close();
            } finally {
                // 关闭低层流。
                in.close();
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            log.info("下载图片出错" + picUrl);
        }
        httpclient.close();
    }

}
