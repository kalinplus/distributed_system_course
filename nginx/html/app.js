// E-commerce System JavaScript

const API_BASE = '/api';

/**
 * Search for product by ID
 */
async function searchProduct() {
    const productId = document.getElementById('productId').value;
    const resultBox = document.getElementById('productResult');

    if (!productId || productId < 1) {
        resultBox.innerHTML = '<p class="error">请输入有效的商品ID</p>';
        return;
    }

    // Show loading state
    resultBox.innerHTML = '<p class="loading">加载中</p>';

    try {
        const response = await fetch(`${API_BASE}/products/${productId}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        // Get instance info from response header
        const instanceId = response.headers.get('X-Instance-Id') || 'unknown';
        document.getElementById('instanceInfo').textContent = `Instance: ${instanceId}`;

        if (!response.ok) {
            const error = await response.json();
            resultBox.innerHTML = `<p class="error">${error.message || '商品不存在'}</p>`;
            return;
        }

        const product = await response.json();
        renderProduct(product);
    } catch (error) {
        console.error('Error:', error);
        resultBox.innerHTML = `<p class="error">请求失败: ${error.message}</p>`;
    }
}

/**
 * Render product data to HTML
 */
function renderProduct(product) {
    const resultBox = document.getElementById('productResult');

    const html = `
        <div class="product-card">
            <h3 class="product-name">${product.name || '商品名称'}</h3>
            <p class="product-detail">描述: ${product.description || '暂无描述'}</p>
            <p class="product-detail">状态: ${getStatusText(product.status)}</p>
            <div class="product-price">¥${product.price || '0.00'}</div>
            <div class="product-stock">库存: ${product.stock || 0}</div>
        </div>
    `;

    resultBox.innerHTML = html;
}

/**
 * Get status text from status code
 */
function getStatusText(status) {
    const statusMap = {
        0: '已下架',
        1: '在售',
        2: '预售中'
    };
    return statusMap[status] || '未知';
}

// Add Enter key support for search
document.getElementById('productId').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        searchProduct();
    }
});

// Auto-search on page load if product ID is provided
window.addEventListener('load', function() {
    const productId = document.getElementById('productId').value;
    if (productId) {
        // Optionally auto-search on load
        // searchProduct();
    }
});
