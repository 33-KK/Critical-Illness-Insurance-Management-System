import type { ComposeOption } from "echarts/core";
import type { BarSeriesOption, LineSeriesOption, PieSeriesOption } from "echarts/charts";
import type {
  GraphicComponentOption,
  GridComponentOption,
  LegendComponentOption,
  TooltipComponentOption,
} from "echarts/components";

type MoneyValue = number | string;

export interface MonthlyActivity {
  month: string;
  amount: MoneyValue;
  visits: number;
}

export interface AuditStatusCount {
  name: string;
  value: number;
}

export interface DashboardSummary {
  insuredCount: number;
  visitCount: number;
  pendingCount: number;
  reimbursedAmount: MoneyValue;
  todayVisits: number;
  totalBalance: MoneyValue;
  monthly: MonthlyActivity[];
  statusCounts: AuditStatusCount[];
  recentVisits: {
    id: number;
    patientName: string;
    visitNo: string;
    totalAmount: MoneyValue;
    auditStatus: string;
    createdAt: string;
  }[];
}

export type DashboardChartOption = ComposeOption<
  | BarSeriesOption
  | LineSeriesOption
  | PieSeriesOption
  | GridComponentOption
  | TooltipComponentOption
  | LegendComponentOption
  | GraphicComponentOption
>;

export function buildTrendOption(monthly: readonly MonthlyActivity[] = []): DashboardChartOption {
  return {
    color: ["#2563eb", "#16866a"],
    textStyle: { fontFamily: '"Microsoft YaHei", sans-serif', color: "#52647d" },
    tooltip: {
      trigger: "axis",
      backgroundColor: "#fff",
      borderColor: "#e2e8f0",
      borderWidth: 1,
      textStyle: { color: "#172b4d", fontSize: 13 },
      axisPointer: { type: "shadow" },
    },
    legend: {
      bottom: 0,
      icon: "roundRect",
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: "#64748b", fontSize: 12 },
    },
    grid: { left: 48, right: 50, top: 32, bottom: 48 },
    xAxis: {
      type: "category",
      data: monthly.map((x) => x.month.slice(-2) + "月"),
      axisLine: { lineStyle: { color: "#e2e8f0" } },
      axisTick: { show: false },
      axisLabel: { color: "#64748b", fontSize: 12 },
    },
    yAxis: [
      {
        type: "value",
        name: "就诊费用 / 元",
        nameTextStyle: { fontSize: 11, color: "#64748b" },
        splitLine: { lineStyle: { color: "#e8eef6", type: "dashed" } },
        axisLabel: {
          color: "#64748b",
          fontSize: 12,
          formatter: (value: number) => String(value >= 10000 ? value / 10000 + "万" : value),
        },
      },
      {
        type: "value",
        name: "就诊 / 笔",
        nameTextStyle: { fontSize: 11, color: "#64748b" },
        splitLine: { show: false },
        axisLabel: { color: "#64748b", fontSize: 12 },
        minInterval: 1,
      },
    ],
    series: [
      {
        name: "就诊费用",
        type: "bar",
        barMaxWidth: 23,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        data: monthly.map((x) => Number(x.amount)),
      },
      {
        name: "就诊次数",
        type: "line",
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 6,
        lineStyle: { color: "#16866a", width: 2 },
        itemStyle: { color: "#16866a" },
        data: monthly.map((x) => x.visits),
      },
    ],
  };
}

const statusLabels: Record<string, string> = {
  PENDING: "待审核",
  APPROVED: "审核通过",
  REJECTED: "审核拒绝",
};

const statusColors: Record<string, string> = {
  PENDING: "#b7791f",
  待审核: "#b7791f",
  APPROVED: "#16866a",
  已通过: "#16866a",
  审核通过: "#16866a",
  REJECTED: "#c94c4c",
  已拒绝: "#c94c4c",
  审核拒绝: "#c94c4c",
};

export function buildAuditOption(
  counts: readonly AuditStatusCount[] = [],
  visitCount = 0,
): DashboardChartOption {
  return {
    color: ["#b7791f", "#16866a", "#c94c4c"],
    textStyle: { fontFamily: '"Microsoft YaHei", sans-serif' },
    tooltip: {
      trigger: "item",
      borderColor: "#e2e8f0",
      textStyle: { color: "#172b4d", fontSize: 13 },
    },
    legend: {
      bottom: 0,
      itemWidth: 9,
      itemHeight: 9,
      icon: "circle",
      textStyle: { color: "#64748b", fontSize: 12 },
    },
    series: [
      {
        type: "pie",
        radius: ["58%", "77%"],
        center: ["50%", "43%"],
        avoidLabelOverlap: true,
        // Zero visits must not appear as three equally sized audit slices.
        stillShowZeroSum: false,
        itemStyle: { borderColor: "#fff", borderWidth: 5, borderRadius: 5 },
        label: { show: false },
        data: counts.map((status) => ({
          name: statusLabels[status.name] || status.name,
          value: status.value,
          itemStyle: { color: statusColors[status.name] || "#64748b" },
        })),
      },
    ],
    graphic: [
      {
        type: "text",
        left: "center",
        top: "31%",
        style: {
          text: String(visitCount),
          font: "600 30px Arial",
          fill: "#172b4d",
        },
      },
      {
        type: "text",
        left: "center",
        top: "47%",
        style: { text: "累计就诊", font: "12px sans-serif", fill: "#64748b" },
      },
    ],
  };
}
