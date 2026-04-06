import { useQuery } from '@tanstack/react-query';
import { searchProducts } from '../api/productApi';

export function useProductSearch(query) {
  return useQuery({
    queryKey: ['products', query],
    queryFn: () => searchProducts(query),
    enabled: query.length >= 2,
    staleTime: 0, // 항상 최신 데이터 fetch (매번 크롤링)
  });
}
