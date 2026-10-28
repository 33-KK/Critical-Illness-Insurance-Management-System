<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Upload,
  Download,
  Trash2,
  FileText,
  Search,
  FolderOpen,
  RefreshCw,
  CloudUpload,
} from "lucide-vue-next";
import { get, api, remove, downloadFile, dateTime, showError } from "../api";
import type { Row } from "../api";
import PageHeader from "../components/PageHeader.vue";
const rows = ref<Row[]>([]),
  loading = ref(false),
  uploading = ref(false),
  input = ref<HTMLInputElement>(),
  keyword = ref(""),
  page = ref(1),
  downloading = ref<number>(),
  dragging = ref(false);
const filtered = computed(() =>
    rows.value.filter((r) => r.originalName.toLowerCase().includes(keyword.value.toLowerCase())),
  ),
  paged = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10));
function fileSize(size: number) {
  return size >= 1048576
    ? (size / 1048576).toFixed(2) + " MB"
    : size >= 1024
      ? (size / 1024).toFixed(1) + " KB"
      : size + " B";
}
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/files");
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
async function upload(file?: File) {
  if (!file || uploading.value) return;
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning("请选择不超过10MB的文件");
    return;
  }
  uploading.value = true;
  try {
    const data = new FormData();
    data.append("file", file);
    await api("/files", { method: "POST", body: data });
    ElMessage.success("文件上传成功");
    await load();
  } catch (e) {
    showError(e);
  } finally {
    uploading.value = false;
    if (input.value) input.value.value = "";
  }
}
async function download(row: Row) {
  downloading.value = row.id;
  try {
    await downloadFile(row.id, row.originalName);
  } catch (e) {
    showError(e);
  } finally {
    downloading.value = undefined;
  }
}
async function del(row: Row) {
  try {
    await ElMessageBox.confirm(`确认删除文件“${row.originalName}”？`, "删除文件", {
      confirmButtonText: "确认删除",
      cancelButtonText: "取消",
      type: "warning",
    });
    await remove("/files/" + row.id);
    ElMessage.success("文件已删除");
    await load();
  } catch (e) {
    if (e instanceof Error) showError(e);
  }
}
function drop(event: DragEvent) {
  dragging.value = false;
  upload(event.dataTransfer?.files[0]);
}
onMounted(load);
</script>
<template>
  <PageHeader
    title="文件中心"
    description="集中管理业务附件与工作资料，文件仅对上传者与管理员开放。"
  >
    <el-button type="primary" :loading="uploading" @click="input?.click()">
      <Upload :size="16" />
      上传文件
    </el-button>
  </PageHeader>
  <input
    ref="input"
    type="file"
    hidden
    @change="(e) => upload((e.target as HTMLInputElement).files?.[0])"
  />
  <div
    class="upload-zone"
    :class="{ dragging }"
    @dragover.prevent="dragging = true"
    @dragleave.prevent="dragging = false"
    @drop.prevent="drop"
    @click="input?.click()"
  >
    <span class="upload-zone-icon"><CloudUpload :size="29" :stroke-width="1.5" /></span>
    <div>
      <strong>{{ uploading ? "正在上传文件…" : "将文件拖到此处，或点击上传" }}</strong>
      <p>支持常用文档与图片，单个文件不超过 10 MB</p>
    </div>
    <span class="subtle-pill">安全存储 · 身份认证下载</span>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="搜索文件名称"
        clearable
        @input="page = 1"
      >
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-button text @click="load">
        <RefreshCw :size="15" />
        刷新
      </el-button>
      <span class="toolbar-count">
        {{ filtered.length }} 个文件 · {{ fileSize(rows.reduce((s, r) => s + Number(r.size), 0)) }}
      </span>
    </div>
    <el-table v-loading="loading" :data="paged">
      <el-table-column label="文件名称" min-width="260">
        <template #default="{ row }">
          <div class="file-name">
            <span class="file-type-icon"><FileText :size="21" /></span>
            <div>
              <strong>{{ row.originalName }}</strong>
              <small>{{ row.contentType }}</small>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="文件大小" width="120">
        <template #default="{ row }">{{ fileSize(Number(row.size)) }}</template>
      </el-table-column>
      <el-table-column prop="uploaderName" label="上传人" width="135" />
      <el-table-column label="上传时间" width="185">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" :loading="downloading === row.id" @click="download(row)">
            下载
          </el-button>
          <el-button link type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无文件，上传一份工作资料吧" /></template>
    </el-table>
    <div class="pagination">
      <span>服务器验证每一次文件访问权限</span>
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="filtered.length"
        layout="total, prev, pager, next"
      />
    </div>
  </section>
</template>
