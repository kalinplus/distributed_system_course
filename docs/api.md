# API 接口定义

本文档定义各微服务的 RESTful API 接口。

## 接口规范

### 通用约定

- 基础路径：`/api`
- 请求 Content-Type：`application/json`
- 响应编码：`UTF-8`
- 认证方式：Token（登录后获取）

### 通用响应格式

```json
// 成功响应
{
  "code": 200,
  "message": "success",
  "data": {}
}

// 失败响应
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```

### 状态码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或Token无效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 1. 用户服务（User Service）

### 1.1 用户注册

**POST** `/api/user/register`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | string | 是 | 用户名（3-20字符） |
| password | string | 是 | 密码（6-20字符） |
| phone | string | 否 | 手机号 |
| email | string | 否 | 邮箱 |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "zhangsan"
  }
}
```

### 1.2 用户登录

**POST** `/api/user/login`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | string | 是 | 用户名或手机号 |
| password | string | 是 | 密码 |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "username": "zhangsan"
  }
}
```

### 1.3 用户登出

**POST** `/api/user/logout`

**请求头：**

| 参数名 | 说明 |
|--------|------|
| Authorization | Bearer Token |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 1.4 获取当前用户信息

**GET** `/api/user/info`

**请求头：**

| 参数名 | 说明 |
|--------|------|
| Authorization | Bearer Token |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "phone": "13800138000",
    "email": "zhangsan@example.com",
    "createdAt": "2024-01-01 10:00:00"
  }
}
```

---

## 2. 商品服务（Product Service）

### 2.1 获取商品列表

**GET** `/api/product/list`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页数量，默认10 |
| keyword | string | 否 | 搜索关键字 |
| categoryId | int | 否 | 分类ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "name": "iPhone 15",
        "price": 5999.00,
        "stock": 100,
        "status": 1,
        "description": "苹果手机"
      }
    ],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

### 2.2 获取商品详情

**GET** `/api/product/{id}`

**路径参数：**

| 参数名 | 类型 | 说明 |
|--------|------|------|
| id | int | 商品ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "iPhone 15",
    "price": 5999.00,
    "stock": 100,
    "status": 1,
    "description": "苹果手机",
    "createdAt": "2024-01-01 10:00:00",
    "updatedAt": "2024-01-01 10:00:00"
  }
}
```

### 2.3 新增商品（后台）

**POST** `/api/product`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| name | string | 是 | 商品名称 |
| price | decimal | 是 | 价格 |
| stock | int | 是 | 库存数量 |
| description | string | 否 | 商品描述 |
| status | int | 否 | 状态（0:下架 1:上架），默认1 |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1
  }
}
```

### 2.4 更新商品（后台）

**PUT** `/api/product/{id}`

**路径参数：**

| 参数名 | 类型 | 说明 |
|--------|------|------|
| id | int | 商品ID |

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| name | string | 否 | 商品名称 |
| price | decimal | 否 | 价格 |
| description | string | 否 | 商品描述 |
| status | int | 否 | 状态 |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 2.5 商品搜索（ElasticSearch）

**GET** `/api/products/search`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| q | string | 是 | 搜索关键词（全文匹配商品名称） |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "iPhone 15 Pro",
      "description": "Apple iPhone 15 Pro 256GB",
      "price": 8999.00,
      "stock": 100,
      "category": "手机",
      "imageUrl": "https://example.com/iphone15.jpg",
      "status": 1
    }
  ]
}
```

**说明：**
- 基于 ElasticSearch `MatchQuery` 对商品名称进行全文检索。
- 服务启动时 `@PostConstruct` 自动从 MySQL 全量同步商品数据到 ES。
- 端口：通过 nginx 网关访问 `http://localhost/api/products/search?q=iPhone`。

### 2.6 删除商品（后台）

**DELETE** `/api/product/{id}`

**路径参数：**

| 参数名 | 类型 | 说明 |
|--------|------|------|
| id | int | 商品ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 3. 库存服务（Inventory Service）

### 3.1 查询商品库存

**GET** `/api/inventory/{productId}`

**路径参数：**

| 参数名 | 类型 | 说明 |
|--------|------|------|
| productId | int | 商品ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "productId": 1,
    "stock": 100,
    "reservedStock": 0
  }
}
```

### 3.2 扣减库存（内部调用）

**POST** `/api/inventory/deduct`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| productId | int | 是 | 商品ID |
| quantity | int | 是 | 扣减数量 |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true
  }
}
```

### 3.3 恢复库存（订单取消时）

**POST** `/api/inventory/revert`

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| productId | int | 是 | 商品ID |
| quantity | int | 是 | 恢复数量 |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true
  }
}
```

---

## 4. 订单服务（Order Service）

### 4.1 创建订单

**POST** `/api/order/create`

**请求头：**

| 参数名 | 说明 |
|--------|------|
| Authorization | Bearer Token |

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| items | array | 是 | 订单商品列表 |

**items 数组元素：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| productId | int | 是 | 商品ID |
| quantity | int | 是 | 购买数量 |

**请求示例：**

```json
{
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ]
}
```

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": 1001,
    "orderNo": "ORD2024010110000001",
    "totalAmount": 11998.00,
    "status": "PENDING_PAYMENT"
  }
}
```

### 4.2 订单列表

**GET** `/api/order/list`

**请求头：**

| 参数名 | 说明 |
|--------|------|
| Authorization | Bearer Token |

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页数量，默认10 |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "orderId": 1001,
        "orderNo": "ORD2024010110000001",
        "totalAmount": 11998.00,
        "status": "PENDING_PAYMENT",
        "createdAt": "2024-01-01 10:00:00"
      }
    ],
    "total": 10,
    "page": 1,
    "pageSize": 10
  }
}
```

### 4.3 订单详情

**GET** `/api/order/{orderId}`

**请求头：**

| 参数名 | 说明 |
|--------|------|
| Authorization | Bearer Token |

**路径参数：**

| 参数名 | 类型 | 说明 |
|--------|------|------|
| orderId | int | 订单ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": 1001,
    "orderNo": "ORD2024010110000001",
    "totalAmount": 11998.00,
    "status": "PENDING_PAYMENT",
    "items": [
      {
        "productId": 1,
        "productName": "iPhone 15",
        "price": 5999.00,
        "quantity": 2,
        "amount": 11998.00
      }
    ],
    "createdAt": "2024-01-01 10:00:00",
    "updatedAt": "2024-01-01 10:00:00"
  }
}
```

### 4.4 取消订单

**POST** `/api/order/{orderId}/cancel`

**请求头：**

| 参数名 | 说明 |
|--------|------|
| Authorization | Bearer Token |

**路径参数：**

| 参数名 | 类型 | 说明 |
|--------|------|------|
| orderId | int | 订单ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 4.5 模拟支付

**POST** `/api/order/{orderId}/pay`

**请求头：**

| 参数名 | 说明 |
|--------|------|
| Authorization | Bearer Token |

**路径参数：**

| 参数名 | 类型 | 说明 |
|--------|------|------|
| orderId | int | 订单ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": 1001,
    "status": "PAID"
  }
}
```

---

## 附录：订单状态说明

| 状态 | 说明 |
|------|------|
| PENDING_PAYMENT | 待支付 |
| PAID | 已支付 |
| CANCELLED | 已取消 |

## 附录：商品状态说明

| 状态 | 说明 |
|------|------|
| 0 | 下架 |
| 1 | 上架 |
