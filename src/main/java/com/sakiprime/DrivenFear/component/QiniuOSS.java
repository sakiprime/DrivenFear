package com.sakiprime.DrivenFear.component;

import cn.hutool.core.io.FileUtil;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.sakiprime.DrivenFear.common.util.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

@Component
public class QiniuOSS {
    @Value("${qiniu.access-key}")
    private String accessKey;
    @Value("${qiniu.secret-key}")
    private String secretKey;
    @Value("${qiniu.bucket-name}")
    private String bucketName;
    @Value("${qiniu.domain}")
    private String domain;

    private static final List<String> ALLOW_SUFFIX = Arrays.asList("jpg", "jpeg", "png", "gif");
    private static final List<String> ALLOW_VIDEO_SUFFIX = Arrays.asList("mp4", "mov", "avi", "wmv", "flv", "mkv", "webm");

    private static Auth auth;
    private static Configuration cfg;
    private static BucketManager bucketManager;

    @PostConstruct
    public void init() {
        auth = Auth.create(accessKey, secretKey);
        cfg = new Configuration(Region.region0());
        bucketManager = new BucketManager(auth, cfg);
    }

    public Result<String> uploadAvatar(MultipartFile file, String userId) throws Exception {

        if(file.isEmpty() || userId == null || userId.trim().isEmpty()){
            return Result.fail(400,"不能上传空头像文件");
        }
        //先校验文件大小，更轻。
        long maxSize = 3 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new Exception("头像大小不能超过3MB");
        }

        String suffix = FileUtil.extName(file.getOriginalFilename());
        String ext = (suffix == null ? "" : suffix.toLowerCase());
        if (!ALLOW_SUFFIX.contains(ext)) {
            throw new Exception("仅支持jpg、jpeg、png、gif格式");
        }


        String upToken = auth.uploadToken(bucketName);

        UploadManager uploadManager = new UploadManager(cfg);
        //hutool取出来的后缀不带.需要手动加
        //头像不属于敏感资源，可以采用有意义命名。
        String key = "avatar/user_" + userId + "_avatar." + ext;

        uploadManager.put(file.getInputStream(), key, upToken, null, null);
        return Result.success(domain + "/" + key);
    }

    /**
     * 获取资源
     *
     * @param sourceUrl 源url
     * @param prefix    前缀
     * @return {@link Result }<{@link String }>
     * @throws Exception 异常
     */
    public Result<String> fetchResource(String sourceUrl, String prefix, String userId) throws Exception {

        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            return Result.fail(400, "源URL不能为空");
        }

        String ext = getResourceExt(sourceUrl);
        if (ext.isEmpty()) {
            return Result.fail(400, "无法识别资源类型");
        }

        if (!ALLOW_SUFFIX.contains(ext) && !ALLOW_VIDEO_SUFFIX.contains(ext)) {
            return Result.fail(400, "不支持的资源格式，仅支持图片(jpg/jpeg/png/gif)和视频(mp4/mov/avi/wmv/flv/mkv/webm)");
        }

        String key = "resource/" + userId + "/" + prefix + "." + ext;
        bucketManager.fetch(sourceUrl, bucketName, key);
        return Result.success(domain + "/" + key);
    }

    /**
     * 获取资源后缀名
     *
     * @param url 网址
     * @return {@link String }
     */
    private String getResourceExt(String url) {

        String cleanUrl = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
        String ext = FileUtil.extName(cleanUrl);
        return ext.toLowerCase();
    }

}
