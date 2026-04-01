import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

export const searchProducts = (query) =>
  api.get('/products/search', { params: { query } }).then(r => r.data);

export const crawlMusinsa = (query) =>
  api.post('/admin/crawl/musinsa', null, { params: { query } }).then(r => r.data);
