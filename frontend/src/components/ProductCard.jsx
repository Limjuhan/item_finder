const PLATFORM_LABELS = {
  musinsa: '무신사',
  '29cm': '29CM',
  coupang: '쿠팡',
};

function formatPrice(price) {
  return price?.toLocaleString('ko-KR') + '원';
}

export default function ProductCard({ product }) {
  const { productName, brand, imageUrl, prices } = product;
  const sortedPrices = [...(prices || [])].sort((a, b) => a.price - b.price);
  const lowestPrice = sortedPrices[0];
  const productUrl = lowestPrice?.url;

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden hover:shadow-md transition-shadow">
      {imageUrl && (
        <a href={productUrl} target="_blank" rel="noreferrer">
          <img
            src={imageUrl}
            alt={productName}
            className="w-full h-52 object-cover object-top"
            onError={e => { e.target.style.display = 'none'; }}
          />
        </a>
      )}
      <div className="p-4">
        {brand && (
          <span className="text-xs font-semibold text-indigo-600 uppercase tracking-wide">
            {brand}
          </span>
        )}
        <h3 className="mt-1 text-sm font-medium text-gray-800 line-clamp-2">
          {productUrl
            ? <a href={productUrl} target="_blank" rel="noreferrer" className="hover:text-indigo-600">{productName}</a>
            : productName}
        </h3>

        {lowestPrice && (
          <p className="mt-2 text-lg font-bold text-gray-900">
            최저 {formatPrice(lowestPrice.price)}
          </p>
        )}

        <div className="mt-3 space-y-1">
          {sortedPrices.map((p) => (
            <div key={p.platform} className="flex items-center justify-between text-xs">
              <span className="text-gray-500">{PLATFORM_LABELS[p.platform] || p.platform}</span>
              <div className="flex items-center gap-2">
                {p.discountRate > 0 && (
                  <span className="text-red-500 font-medium">{p.discountRate}%</span>
                )}
                {p.url ? (
                  <a
                    href={p.url}
                    target="_blank"
                    rel="noreferrer"
                    className="font-semibold text-gray-800 hover:text-indigo-600"
                  >
                    {formatPrice(p.price)}
                  </a>
                ) : (
                  <span className="font-semibold text-gray-800">{formatPrice(p.price)}</span>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
