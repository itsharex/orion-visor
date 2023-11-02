import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import axios from 'axios';
import { Message } from '@arco-design/web-vue';
import { useUserStore } from '@/store';
import { getToken } from '@/utils/auth';
import { reLoginTipsKey } from '@/types/symbol';

export interface HttpResponse<T = unknown> {
  msg: string;
  code: number;
  data: T;
}

axios.defaults.timeout = 10000;
axios.defaults.promptBizErrorMessage = true;
if (import.meta.env.VITE_API_BASE_URL) {
  axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;
}

axios.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // 获取 token
    const token = getToken();
    if (token) {
      if (!config.headers) {
        config.headers = {};
      }
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

axios.interceptors.response.use(
  (response: AxiosResponse<HttpResponse>) => {
    // 不转换
    if (response.config.unwrap) {
      return response;
    }
    const res = response.data;
    const { code } = res;
    // 200 成功
    if (code === 200) {
      return res;
    }
    // 异常判断
    if ([401, 700, 701, 702, 703].includes(code)) {
      // 提示
      Message.error({
        content: res.msg || 'Error',
        duration: 5 * 1000,
      });
      // 认证异常
      setTimeout(async () => {
        // 设置错误信息 登录页面重新提示 (重新加载会刷掉提示)
        window.sessionStorage.setItem(reLoginTipsKey, res.msg);
        // 登出
        await useUserStore().logout();
        // 重新加载自动跳转登录页面
        window.location.reload();
      });
    } else {
      // 其他异常 判断是否弹出错误信息
      if (response.config.promptBizErrorMessage) {
        Message.error({
          content: res.msg || 'Error',
          duration: 5 * 1000,
        });
      }
    }
    return Promise.reject(new Error(res.msg || 'Error'));
  },
  (error) => {
    Message.error({
      content: error.msg || '请求失败',
      duration: 5 * 1000,
    });
    return Promise.reject(error);
  }
);
