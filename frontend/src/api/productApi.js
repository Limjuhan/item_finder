import axios from 'axios';

const baseURL = import.meta.env.VITE_API_URL ?? '/api';
const api = axios.create({ baseURL });

export const searchProducts = (query) =>
  api.get('/products/search', { params: { query } }).then(r => r.data);

export const crawlMusinsa = (query) =>
  api.post('/admin/crawl/musinsa', null, { params: { query } }).then(r => r.data);
