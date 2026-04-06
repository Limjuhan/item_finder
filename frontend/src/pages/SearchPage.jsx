import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import SearchBar from '../components/SearchBar';
import ProductCard from '../components/ProductCard';
import { useProductSearch } from '../hooks/useProductSearch';
import { crawlMusinsa } from '../api/productApi';

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useProductSearch(query);

  const handleSearch = async (searchQuery) => {
    setQuery(searchQuery);
    if (searchQuery.length >= 2) {
      try {
        // 크롤링 실행
        await crawlMusinsa(searchQuery);
        // 크롤링 완료 후 캐시 무효화 → 새 데이터 자동 fetch
        queryClient.invalidateQueries({ queryKey: ['products', searchQuery] });
      } catch (e) {
        console.error('Crawling failed:', e);
      }
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 py-4 flex flex-col items-center gap-3">
          <h1 className="text-xl font-bold text-indigo-700 tracking-tight">
            ItemFinder — 패션 가격 비교
          </h1>
          <SearchBar onSearch={handleSearch} />
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-8">
        {!query && (
          <div className="text-center mt-16 space-y-4">
            <p className="text-gray-600 text-sm font-medium">
              정확한 검색을 위해 상품명을 입력하세요
            </p>
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 max-w-md mx-auto">
              <p className="text-gray-700 text-sm mb-2"><strong>검색 예시:</strong></p>
              <ul className="text-gray-600 text-xs space-y-1 text-left">
                <li>✓ 나이키 에어포스</li>
                <li>✓ 아이더 플렉션</li>
                <li>✓ 아디다스 운동화</li>
              </ul>
            </div>
            <p className="text-gray-400 text-xs mt-4">
              상품명이 포함된 검색어를 사용하면 더 정확한 결과를 얻을 수 있습니다
            </p>
          </div>
        )}

        {query.length > 0 && query.length < 2 && (
          <p className="text-center text-gray-400 mt-20 text-sm">두 글자 이상 입력하세요</p>
        )}

        {isLoading && (
          <div className="flex justify-center mt-20">
            <div className="w-8 h-8 border-4 border-indigo-300 border-t-indigo-600 rounded-full animate-spin" />
          </div>
        )}

        {error && (
          <p className="text-center text-red-500 mt-20 text-sm">
            오류가 발생했습니다. 서버가 실행 중인지 확인하세요.
          </p>
        )}

        {data && data.length === 0 && (
          <p className="text-center text-gray-400 mt-20 text-sm">
            "{query}"에 대한 검색 결과가 없습니다.
            <br />
            <span className="text-xs">크롤링 후 다시 검색해 보세요.</span>
          </p>
        )}

        {data && data.length > 0 && (
          <>
            <p className="text-xs text-gray-400 mb-4">{data.length}개 상품</p>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {data.map(product => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          </>
        )}
      </main>
    </div>
  );
}
