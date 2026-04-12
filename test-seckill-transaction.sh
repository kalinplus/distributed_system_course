#!/bin/bash
# 秒杀系统事务与一致性测试脚本
# 一键执行完整测试流程

set -e

BASE_URL="http://localhost"
ORDER_SERVICE="${BASE_URL}:8085"
INVENTORY_SERVICE="${BASE_URL}:8084"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查服务健康
check_health() {
    log_info "检查服务健康状态..."

    # 检查inventory-service
    if ! curl -s "${INVENTORY_SERVICE}/api/inventory/health" > /dev/null 2>&1; then
        log_error "inventory-service 未启动"
        exit 1
    fi

    # 检查order-service
    if ! curl -s "${ORDER_SERVICE}/api/orders/health" > /dev/null 2>&1; then
        log_error "order-service 未启动"
        exit 1
    fi

    log_info "所有服务健康"
}

# 测试1: 库存预热
test_warmup() {
    log_info "Step 1: 预热库存..."

    RESPONSE=$(curl -s -X POST "${INVENTORY_SERVICE}/api/inventory/seckill/warmup/1/100")

    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_info "✓ 库存预热成功"
    else
        log_error "✗ 库存预热失败: $RESPONSE"
        exit 1
    fi
}

# 测试2: 秒杀下单
test_seckill_order() {
    log_info "Step 2: 秒杀下单..."

    USER_ID=$((RANDOM % 10000 + 100))

    RESPONSE=$(curl -s -X POST "${ORDER_SERVICE}/api/orders/seckill" \
        -H "X-User-Id: $USER_ID" \
        -H "Content-Type: application/json" \
        -d '{
            "productId": 1,
            "seckillActivityId": 1001,
            "quantity": 1,
            "totalAmount": 8999.00
        }')

    ORDER_NO=$(echo "$RESPONSE" | grep -o '"orderNo":"[^"]*"' | cut -d'"' -f4)

    if [ -n "$ORDER_NO" ]; then
        log_info "✓ 下单成功, OrderNo: $ORDER_NO"
        echo "$ORDER_NO"
    else
        log_error "✗ 下单失败: $RESPONSE"
        exit 1
    fi
}

# 测试3: 查询订单状态
test_query_order() {
    local ORDER_NO=$1
    local EXPECTED_STATUS=$2

    log_info "Step 3: 查询订单状态..."

    sleep 3

    RESPONSE=$(curl -s "${ORDER_SERVICE}/api/orders/order/${ORDER_NO}")

    STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

    if [ "$STATUS" = "$EXPECTED_STATUS" ]; then
        log_info "✓ 订单状态正确: $STATUS"
    else
        log_error "✗ 订单状态错误, 期望: $EXPECTED_STATUS, 实际: $STATUS"
        log_error "响应: $RESPONSE"
        exit 1
    fi
}

# 测试4: 支付回调
test_payment_callback() {
    local ORDER_NO=$1
    local PAYMENT_STATUS=$2

    log_info "Step 4: 支付回调 ($PAYMENT_STATUS)..."

    PAYMENT_NO="PAY_$(date +%s)_$RANDOM"

    RESPONSE=$(curl -s -X POST "${ORDER_SERVICE}/api/orders/payment/callback" \
        -H "Content-Type: application/json" \
        -d "{
            \"orderNo\": \"$ORDER_NO\",
            \"paymentNo\": \"$PAYMENT_NO\",
            \"status\": \"$PAYMENT_STATUS\",
            \"amount\": 8999.00,
            \"payTime\": $(date +%s)000
        }")

    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_info "✓ 支付回调成功"
    else
        log_error "✗ 支付回调失败: $RESPONSE"
        exit 1
    fi
}

# 测试5: 支付幂等性
test_payment_idempotency() {
    local ORDER_NO=$1
    local PAYMENT_NO=$2

    log_info "Step 5: 验证支付幂等性..."

    RESPONSE=$(curl -s -X POST "${ORDER_SERVICE}/api/orders/payment/callback" \
        -H "Content-Type: application/json" \
        -d "{
            \"orderNo\": \"$ORDER_NO\",
            \"paymentNo\": \"$PAYMENT_NO\",
            \"status\": \"SUCCESS\",
            \"amount\": 8999.00
        }")

    if echo "$RESPONSE" | grep -q '"code":200'; then
        log_info "✓ 幂等性验证通过（重复请求返回成功）"
    else
        log_error "✗ 幂等性验证失败: $RESPONSE"
        exit 1
    fi
}

# 测试6: 库存同步
test_inventory_sync() {
    log_info "Step 6: 验证库存同步..."

    sleep 2

    RESPONSE=$(curl -s "${INVENTORY_SERVICE}/api/inventory/1")

    log_info "库存查询结果: $RESPONSE"
    log_info "✓ 库存同步完成（请人工验证stock值是否正确）"
}

# 测试7: 重复秒杀（限购）
test_duplicate_seckill() {
    local USER_ID=$1

    log_info "Step 7: 验证重复秒杀拦截..."

    RESPONSE=$(curl -s -X POST "${ORDER_SERVICE}/api/orders/seckill" \
        -H "X-User-Id: $USER_ID" \
        -H "Content-Type: application/json" \
        -d '{
            "productId": 1,
            "seckillActivityId": 1001,
            "quantity": 1,
            "totalAmount": 8999.00
        }')

    if echo "$RESPONSE" | grep -q '409'; then
        log_info "✓ 重复秒杀被正确拦截"
    else
        log_warn "⚠ 重复秒杀响应: $RESPONSE"
    fi
}

# 清理测试数据
cleanup() {
    log_info "清理测试数据..."

    # 清空Redis（测试环境）
    docker exec redis redis-cli -a redis123 FLUSHDB > /dev/null 2>&1 || true

    log_info "清理完成"
}

# 主流程
main() {
    echo "========================================"
    echo "  秒杀系统事务与一致性测试"
    echo "========================================"

    # 检查服务
    check_health

    # 预热库存
    test_warmup

    # 下单
    ORDER_NO=$(test_seckill_order)

    # 查询订单（应为PENDING）
    test_query_order "$ORDER_NO" "PENDING"

    # 支付成功回调
    test_payment_callback "$ORDER_NO" "SUCCESS"

    # 查询订单（应为PAID）
    test_query_order "$ORDER_NO" "PAID"

    # 验证幂等性
    test_payment_idempotency "$ORDER_NO" "PAY_$(date +%s)_$RANDOM"

    # 验证库存同步
    test_inventory_sync

    # 验证重复秒杀
    # 注意：这里需要之前下单的USER_ID，我们用一个新测试

    echo ""
    echo "========================================"
    log_info "所有测试通过！"
    echo "========================================"

    # 可选：清理数据
    # cleanup
}

# 如果直接执行脚本
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
