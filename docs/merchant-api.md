# foodhub-merchant 接口说明

这个文档记录当前 `foodhub-merchant` 已实现的 mock 接口。mock 模式不依赖 MySQL、Redis、Nacos，适合先联调接口契约和前端页面；以后接数据库时保持同一套 URL、请求字段和响应结构。

## 启动和测试

在项目根目录执行：

```powershell
cd "D:\Java shixun\foodhub"
```

单元测试：

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd" --batch-mode --no-transfer-progress -pl foodhub-merchant -am test
```

启动 mock 服务：

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd" --batch-mode --no-transfer-progress -f foodhub-merchant\pom.xml spring-boot:run "-Dspring-boot.run.profiles=mock"
```

基础地址：

```text
http://localhost:8102
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8102/actuator/health
```

## 通用响应

成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {}
}
```

业务或参数错误会返回同样的外层结构，例如：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "name: must not be blank",
  "data": null
}
```

分页响应的 `data`：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 0
}
```

## 读取接口

### 查询分类

```http
GET /api/categories
```

返回字段：

```json
{
  "id": 1,
  "name": "分类名称",
  "sortOrder": 10
}
```

### 查询商家列表

```http
GET /api/merchants?keyword=&categoryId=&sort=rating&page=1&size=20
```

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `keyword` | 否 | 按商家名或地址模糊查询 |
| `categoryId` | 否 | 分类 ID |
| `sort` | 否 | `rating` 或 `sales`，默认 `rating` |
| `page` | 否 | 页码，默认 `1` |
| `size` | 否 | 每页数量，默认 `20`，最大 `100` |

商家字段：

```json
{
  "id": 101,
  "ownerUserId": 7,
  "categoryId": 1,
  "categoryName": "分类名称",
  "name": "商家名称",
  "address": "商家地址",
  "longitude": 116.3975,
  "latitude": 39.9087,
  "rating": 4.8,
  "salesCount": 1286,
  "businessStatus": "OPEN",
  "status": "ACTIVE",
  "createdAt": "2026-09-15T00:00:00Z",
  "updatedAt": "2026-09-15T00:00:00Z"
}
```

### 查询商家详情

```http
GET /api/merchants/{merchantId}
```

### 查询菜品列表

```http
GET /api/foods?merchantId=&categoryId=&keyword=&sort=sales&page=1&size=20
```

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `merchantId` | 否 | 商家 ID |
| `categoryId` | 否 | 商家分类 ID |
| `keyword` | 否 | 按菜品名或商家名模糊查询 |
| `sort` | 否 | `sales` 或 `price`，默认 `sales` |
| `page` | 否 | 页码，默认 `1` |
| `size` | 否 | 每页数量，默认 `20`，最大 `100` |

菜品字段：

```json
{
  "id": 1001,
  "merchantId": 101,
  "merchantName": "商家名称",
  "name": "菜品名称",
  "price": 42.00,
  "imageUrl": null,
  "salesCount": 420,
  "onSale": true,
  "status": "ACTIVE",
  "createdAt": "2026-09-15T00:00:00Z",
  "updatedAt": "2026-09-15T00:00:00Z"
}
```

### 查询菜品详情

```http
GET /api/foods/{foodId}
```

## 写入接口

写入接口需要请求头：

```http
X-User-Id: 7
X-User-Role: MERCHANT
```

允许角色：`MERCHANT`、`ADMIN`。`MERCHANT` 只能修改自己名下的商家和菜品，`ADMIN` 可以修改全部。

### 新增商家

```http
POST /api/merchants
Content-Type: application/json
X-User-Id: 42
X-User-Role: MERCHANT
```

请求体：

```json
{
  "name": "Campus Lunch",
  "categoryId": 1,
  "address": "Road 1",
  "longitude": 116.3000,
  "latitude": 39.9000
}
```

### 修改商家

```http
PATCH /api/merchants/{merchantId}
Content-Type: application/json
X-User-Id: 7
X-User-Role: MERCHANT
```

请求体字段都可选：

```json
{
  "name": "New Name",
  "categoryId": 1,
  "address": "New Address",
  "longitude": 116.4000,
  "latitude": 39.9000,
  "businessStatus": "OPEN",
  "status": "ACTIVE"
}
```

枚举：

```text
businessStatus: OPEN, CLOSED
status: ACTIVE, INACTIVE
```

### 新增菜品

```http
POST /api/foods
Content-Type: application/json
X-User-Id: 7
X-User-Role: MERCHANT
```

请求体：

```json
{
  "merchantId": 101,
  "name": "Spicy Chicken",
  "price": 29.90,
  "imageUrl": "https://example.com/food.jpg"
}
```

### 修改菜品

```http
PATCH /api/foods/{foodId}
Content-Type: application/json
X-User-Id: 7
X-User-Role: MERCHANT
```

请求体字段都可选：

```json
{
  "name": "New Food Name",
  "price": 31.90,
  "imageUrl": "https://example.com/new-food.jpg",
  "onSale": true,
  "status": "ACTIVE"
}
```

## PowerShell 调用示例

查询商家：

```powershell
Invoke-RestMethod "http://localhost:8102/api/merchants?page=1&size=2&sort=sales"
```

新增商家：

```powershell
$body = @{
  name = "Campus Lunch"
  categoryId = 1
  address = "Road 1"
  longitude = 116.3000
  latitude = 39.9000
} | ConvertTo-Json

Invoke-RestMethod "http://localhost:8102/api/merchants" -Method Post -ContentType "application/json" -Headers @{
  "X-User-Id" = "42"
  "X-User-Role" = "MERCHANT"
} -Body $body
```

越权示例，应该返回 `FORBIDDEN`：

```powershell
$body = @{
  merchantId = 102
  name = "Unauthorized Food"
  price = 19.90
} | ConvertTo-Json

Invoke-RestMethod "http://localhost:8102/api/foods" -Method Post -ContentType "application/json" -Headers @{
  "X-User-Id" = "7"
  "X-User-Role" = "MERCHANT"
} -Body $body
```
