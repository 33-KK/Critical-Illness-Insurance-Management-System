<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Search, RefreshCw, ArrowDownLeft, ArrowUpRight, WalletCards } from "lucide-vue-next";
import { get, money, dateTime, query, showError } from "../api";
import type { Row } from "../api";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
const rows = ref<Row[]>([]),
  loading = ref(false),
  keyword = ref(""),
  type = ref(""),
  page = ref(1);
const paged = computed(() => rows.value.slice((page.value - 1) * 12, page.value * 12));
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/transactions" + query({ keyword: keyword.value, type: type.value }));
    page.value = 1;
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <PageHeader
    title="医保卡流水"
    description="追踪充值、就诊扣款与欠款补缴，每一笔账户变动都有依据。"
  />
  <div class="mini-stats">
    <div>
      <span class="mini-stat-icon teal"><ArrowDownLeft :size="20" /></span>
      <div>
        <small>当前查询入账</small>
        <strong>¥ {{ money(rows.reduce((s, r) => s + Math.max(0, Number(r.amount)), 0)) }}</strong>
      </div>
    </div>
    <div>
      <span class="mini-stat-icon amber"><ArrowUpRight :size="20" /></span>
      <div>
        <small>当前查询出账</small>
        <strong>
          ¥ {{ money(rows.reduce((s, r) => s + Math.abs(Math.min(0, Number(r.amount))), 0)) }}
        </strong>
      </div>
    </div>
    <div>
      <span class="mini-stat-icon blue"><WalletCards :size="20" /></span>
      <div>
        <small>流水记录</small>
        <strong>
          {{ rows.length }}
          <em>笔</em>
        </strong>
      </div>
    </div>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="搜索参保人、卡号或业务单号"
        clearable
        @keyup.enter="load"
        @clear="load"
      >
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select
        v-model="type"
        placeholder="全部流水类型"
        clearable
        style="width: 165px"
        @change="load"
      >
        <el-option label="账户充值" value="RECHARGE" />
        <el-option label="就诊自付" value="SELF_PAY" />
        <el-option label="拒绝补扣" value="REJECT_DEDUCTION" />
        <el-option label="补缴欠款" value="ARREARS_PAYMENT" />
        <el-option label="医院结算" value="HOSPITAL_SETTLEMENT" />
      </el-select>
      <el-button @click="load">查询</el-button>
      <el-button
        text
        @click="
          keyword = '';
          type = '';
          load();
        "
      >
        <RefreshCw :size="15" />
        重置
      </el-button>
    </div>
    <el-table v-loading="loading" :data="paged">
      <el-table-column prop="patientName" label="参保人" min-width="95" />
      <el-table-column prop="cardNo" label="医保卡号" min-width="145" />
      <el-table-column label="业务类型" width="125">
        <template #default="{ row }"><StatusBadge :value="row.type" /></template>
      </el-table-column>
      <el-table-column label="变动金额" width="135">
        <template #default="{ row }">
          <strong
            :class="
              Number(row.amount) > 0
                ? 'text-success'
                : Number(row.amount) < 0
                  ? 'text-danger'
                  : 'muted'
            "
          >
            {{ Number(row.amount) > 0 ? "+" : "" }}{{ money(row.amount) }}
          </strong>
        </template>
      </el-table-column>
      <el-table-column label="变动后余额" width="135">
        <template #default="{ row }">¥ {{ money(row.balanceAfter) }}</template>
      </el-table-column>
      <el-table-column prop="businessNo" label="业务单号" min-width="155" show-overflow-tooltip />
      <el-table-column prop="operatorName" label="操作人" width="100" />
      <el-table-column label="时间" width="175">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="note" label="备注" min-width="160" show-overflow-tooltip />
      <template #empty><el-empty description="暂无符合条件的资金流水" /></template>
    </el-table>
    <div class="pagination">
      <span>金额为账户实际变动，正数入账、负数出账</span>
      <el-pagination
        v-model:current-page="page"
        :page-size="12"
        :total="rows.length"
        layout="total, prev, pager, next"
      />
    </div>
  </section>
</template>
