/**
 * 获取页面中的CSRF令牌（Spring Security必需）
 * 令牌由Thymeleaf自动注入到页面的meta标签中
 */
function getCsrfToken() {
    const metaTag = document.querySelector('meta[name="_csrf"]');
    if (!metaTag) {
        throw new Error('CSRF令牌未找到，请检查页面配置');
    }
    return metaTag.getAttribute('content');
}

function getTime(){
    return new Date().getTime();
}