<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Search, RefreshCw, ScrollText, ShieldCheck } from "lucide-vue-next";
import { get, query, dateTime, showError } from "../api";
import type { Row } from "../api";
import PageHeader from "../components/PageHeader.vue";
const rows = ref<Row[]>([]),
  loading = ref(false),
  keyword = ref(""),
  type = ref(""),
  page = ref(1),
  detail = ref<Row | null>(null);
const paged = computed(() => rows.value.slice((page.value - 1) * 12, page.value * 12));
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/admin/logs" + query({ keyword: keyword.value, type: type.value }));
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
    title="操作日志"
    description="记录关键操作与业务变更，为问题追踪和审计核验提供依据。"
  />
  <div class="info-note">
    <ShieldCheck :size="17" />
    <span>充值、登记、审核与系统配置等操作由服务端留痕记录。</span>
    <span class="info-note-right">审计追踪</span>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索账号、操作内容或对象"
        clearable
        class="search-input"
        @keyup.enter="load"
        @clear="load"
      >
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-input
        v-model="type"
        placeholder="操作类型（可选）"
        clearable
        style="width: 180px"
        @keyup.enter="load"
      />
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
      <span class="toolbar-count">共 {{ rows.length }} 条日志</span>
    </div>
    <el-table v-loading="loading" :data="paged">
      <el-table-column prop="username" label="操作账号" width="130" />
      <el-table-column prop="action" label="操作类型" min-width="160" />
      <el-table-column prop="target" label="操作对象" min-width="170" show-overflow-tooltip />
      <el-table-column prop="detail" label="操作详情" min-width="270" show-overflow-tooltip />
      <el-table-column prop="ip" label="来源 IP" width="140" />
      <el-table-column label="时间" width="175">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="detail = row">详情</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无符合条件的操作日志" /></template>
    </el-table>
    <div class="pagination">
      <span>操作日志由系统生成，只读保存</span>
      <el-pagination
        v-model:current-page="page"
        :page-size="12"
        :total="rows.length"
        layout="total, prev, pager, next"
      />
    </div>
  </section>
  <el-dialog :model-value="!!detail" title="操作日志详情" width="620px" @close="detail = null">
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="操作账号">{{ detail.username }}</el-descriptions-item>
      <el-descriptions-item label="操作类型">{{ detail.action }}</el-descriptions-item>
      <el-descriptions-item label="操作对象">{{ detail.target }}</el-descriptions-item>
      <el-descriptions-item label="来源 IP">{{ detail.ip }}</el-descriptions-item>
      <el-descriptions-item label="时间">{{ dateTime(detail.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="详情">
        <pre class="log-detail">{{ detail.detail }}</pre>
      </el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>
