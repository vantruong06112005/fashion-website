import re
import os

input_path = 'demo-data/import-demo.sql.bak'
output_path = 'demo-data/import-demo.sql'

with open(input_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
modified_pv = 0
modified_orders_insert = 0
modified_orders_update = 0
modified_pt_insert = 0
modified_pt_update = 0

for line in lines:
    # 0. Add user_addresses update right after customer check
    if "v_customers=0" in line:
        new_lines.append(line)
        new_lines.append("    UPDATE `user_addresses` SET `province_name`='Hồ Chí Minh', `ward_name`='Phường Bến Thành' WHERE `province_name` IS NULL;\n")
        continue

    # 1. product_variants INSERT
    if 'INSERT INTO `product_variants`' in line:
        line = line.replace(
            '`is_active`, `created_at`, `updated_at`)',
            '`weight`, `length`, `width`, `height`, `is_active`, `created_at`, `updated_at`)'
        )
        # Match: VALUES (@fw_p... or @fw_owen_p..., 'SKU', 'Color', 'Size', price, stock, is_active, 'created', 'updated')
        m = re.search(r'VALUES \((@fw_(?:owen_)?p[0-9]+),\s*(\'[^\']+\'),\s*(\'[^\']+\'),\s*(\'[^\']+\'),\s*([0-9]+),\s*([0-9]+),\s*(1|0),', line)
        if m:
            price = int(m.group(5))
            stock = m.group(6)
            is_active = m.group(7)
            if price > 400000:
                w, l, wd, h = 500, 32, 24, 4
            elif price <= 150000:
                w, l, wd, h = 100, 18, 14, 2
            else:
                w, l, wd, h = 250, 28, 20, 3
            old_part = f', {stock}, {is_active},'
            new_part = f', {stock}, {w}, {l}, {wd}, {h}, {is_active},'
            line = line.replace(old_part, new_part)
            modified_pv += 1
        new_lines.append(line)
        continue

    # 2. orders INSERT
    if 'INSERT INTO `orders`' in line:
        line = line.replace(
            '`shipping_address`, `subtotal`',
            '`shipping_address`, `shipping_provider`, `tracking_code`, `subtotal`'
        )
        m = re.search(r'VALUES \(\'(FWD1-([0-9]{8})-([0-9]{3}))\'', line)
        if m:
            full_code = m.group(1)
            date_part = m.group(2)
            seq_part = m.group(3)
            tracking_code = f'GHN{date_part}{seq_part}'
            marker = r"'\)\), (\d+), (\d+),"
            m_sub = re.search(marker, line)
            if m_sub:
                old_sub = m_sub.group(0)
                new_sub = f"')), 'GHN', '{tracking_code}', {m_sub.group(1)}, {m_sub.group(2)},"
                line = line.replace(old_sub, new_sub)
                modified_orders_insert += 1
            else:
                print('COULD NOT MATCH ORDER INSERT LINE:', line[:120])
        new_lines.append(line)
        continue

    # 3. orders UPDATE (legacy branch)
    if 'UPDATE `orders` SET `order_code`=' in line:
        m = re.search(r"`order_code`='(FWD1-([0-9]{8})-([0-9]{3}))'", line)
        if m:
            tracking_code = f'GHN{m.group(2)}{m.group(3)}'
            line = line.replace(
                ', `subtotal`=',
                f", `shipping_provider`='GHN', `tracking_code`='{tracking_code}', `subtotal`="
            )
            modified_orders_update += 1
        new_lines.append(line)
        continue

    # 4. payment_transactions INSERT
    if 'INSERT INTO `payment_transactions`' in line:
        line = line.replace(
            '(`transaction_code`, `amount`',
            '(`transaction_code`, `request_id`, `pay_url`, `amount`'
        )
        m = re.search(r"VALUES \('([^']+)',\s*(\d+)", line)
        if m:
            code = m.group(1)
            amount = m.group(2)
            req_id = f'REQ-{code}'
            if '"method":"COD"' in line or '"direction":"REFUND"' in line:
                pay_url_sql = 'NULL'
            elif '"method":"BANK"' in line:
                m_ord = re.search(r'"order_code":"([^"]+)"', line)
                ord_c = m_ord.group(1) if m_ord else code
                pay_url_sql = f"'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?orderId={ord_c}'"
            else:
                pay_url_sql = 'NULL'
            old_str = f"VALUES ('{code}', {amount}"
            new_str = f"VALUES ('{code}', '{req_id}', {pay_url_sql}, {amount}"
            line = line.replace(old_str, new_str)
            modified_pt_insert += 1
        else:
            print('COULD NOT MATCH PT INSERT LINE:', line[:120])
        new_lines.append(line)
        continue

    # 5. payment_transactions UPDATE (legacy branch)
    if 'UPDATE `payment_transactions` SET `transaction_code`=' in line:
        m = re.search(r"`transaction_code`='([^']+)',\s*`amount`=(\d+)", line)
        if m:
            code = m.group(1)
            amount = m.group(2)
            req_id = f'REQ-{code}'
            if '"method":"COD"' in line or '"direction":"REFUND"' in line:
                pay_url_sql = 'NULL'
            elif '"method":"BANK"' in line:
                m_ord = re.search(r'"order_code":"([^"]+)"', line)
                ord_c = m_ord.group(1) if m_ord else code
                pay_url_sql = f"'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?orderId={ord_c}'"
            else:
                pay_url_sql = 'NULL'
            old_str = f"`transaction_code`='{code}', `amount`={amount}"
            new_str = f"`transaction_code`='{code}', `request_id`='{req_id}', `pay_url`={pay_url_sql}, `amount`={amount}"
            line = line.replace(old_str, new_str)
            modified_pt_update += 1
        new_lines.append(line)
        continue

    # 6. Update validation checks
    if "(v.stock_quantity<0 OR v.price<=0 OR v.color IS NULL OR v.size IS NULL))<>0" in line:
        line = line.replace(
            "(v.stock_quantity<0 OR v.price<=0 OR v.color IS NULL OR v.size IS NULL))<>0",
            "(v.stock_quantity<0 OR v.price<=0 OR v.color IS NULL OR v.size IS NULL OR v.weight IS NULL OR v.length IS NULL OR v.width IS NULL OR v.height IS NULL))<>0"
        )

    new_lines.append(line)

print(f'Modified PV: {modified_pv} / 830')
print(f'Modified Orders Insert: {modified_orders_insert} / 300')
print(f'Modified Orders Update: {modified_orders_update} / 300')
print(f'Modified PT Insert: {modified_pt_insert} / 313')
print(f'Modified PT Update: {modified_pt_update} / 309')

with open(output_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print('Successfully updated import-demo.sql with all fields!')
