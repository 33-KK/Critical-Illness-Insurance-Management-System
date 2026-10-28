import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { init, use } from "echarts/core";
import type { ECharts } from "echarts/core";
import { SVGRenderer } from "echarts/renderers";
import "../src/charts/echarts";
import { buildAuditOption, buildTrendOption } from "../src/charts/dashboard";
import type { DashboardChartOption } from "../src/charts/dashboard";

// Use the production registrations. SVG is only added by this Node test harness
// so actual chart layout/rendering can be checked without a browser or canvas mock.
use([SVGRenderer]);

const charts: ECharts[] = [];
const monthly = [
  { month: "2025-12", amount: "12345.67", visits: 3 },
  { month: "2026-01", amount: 89.5, visits: 1 },
];

function render(option: DashboardChartOption) {
  const chart = init(null, undefined, { renderer: "svg", ssr: true, width: 760, height: 245 });
  charts.push(chart);
  chart.setOption({ ...option, animation: false });
  return chart;
}

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  vi.spyOn(console, "warn").mockImplementation(() => {});
});

afterEach(() => {
  charts.splice(0).forEach((chart) => chart.dispose());
  // A missing on-demand chart/component often only reports a console error.
  expect(console.error).not.toHaveBeenCalled();
  expect(console.warn).not.toHaveBeenCalled();
});

describe("dashboard chart integration", () => {
  it("keeps yuan amounts and visit counts on their respective axes", () => {
    const chart = render(buildTrendOption(monthly));
    const fees = chart.getModel().getSeriesByIndex(0);
    const visits = chart.getModel().getSeriesByIndex(1);

    expect(fees.subType).toBe("bar");
    expect(fees.getData().get("y", 0)).toBe(12345.67);
    expect(fees.getData().get("y", 1)).toBe(89.5);
    expect(fees.coordinateSystem?.type).toBe("cartesian2d");
    expect(visits.subType).toBe("line");
    expect(visits.getData().get("y", 0)).toBe(3);
    expect(visits.get("yAxisIndex")).toBe(1);
  });

  it("renders both chart types, month rollover, legend and axis units", () => {
    const svg = render(buildTrendOption(monthly)).renderToSVGString();

    for (const label of ["12月", "01月", "就诊费用", "就诊次数", "就诊费用 / 元", "就诊 / 笔"])
      expect(svg).toContain(label);
    expect(svg).toContain("<path");
    expect(svg).not.toMatch(/NaN|Infinity|undefined/);
  });

  it("renders audit counts and preserves the backend's Chinese status labels", () => {
    const chart = render(
      buildAuditOption(
        [
          { name: "PENDING", value: 2 },
          { name: "已通过", value: 1 },
          { name: "REJECTED", value: 1 },
        ],
        4,
      ),
    );
    const series = chart.getModel().getSeriesByIndex(0);
    const values = series.getData();
    const svg = chart.renderToSVGString();

    expect(series.subType).toBe("pie");
    expect(values.getName(0)).toBe("待审核");
    expect(values.getName(1)).toBe("已通过");
    expect(values.getName(2)).toBe("审核拒绝");
    expect(values.get("value", 0)).toBe(2);
    for (const label of ["待审核", "已通过", "审核拒绝", "累计就诊"]) expect(svg).toContain(label);
    expect(svg).toMatch(/>4<\/text>/);
  });

  it("does not invent equal-sized audit slices when all counts are zero", () => {
    const chart = render(
      buildAuditOption([
        { name: "待审核", value: 0 },
        { name: "已通过", value: 0 },
        { name: "已拒绝", value: 0 },
      ]),
    );
    const values = chart.getModel().getSeriesByIndex(0).getData();

    for (let index = 0; index < values.count(); index++) {
      const sector = values.getItemLayout(index);
      expect(Math.abs(sector.endAngle - sector.startAngle)).toBe(0);
    }
    const svg = chart.renderToSVGString();
    expect(svg).toContain("累计就诊");
    expect(svg).toMatch(/>0<\/text>/);
    expect(svg).not.toMatch(/NaN|Infinity|undefined/);
  });

  it("renders safely before dashboard data arrives", () => {
    for (const option of [buildTrendOption(), buildAuditOption()]) {
      const chart = render(option);
      expect(chart.getModel().getSeriesByIndex(0).getData().count()).toBe(0);
      expect(chart.renderToSVGString()).not.toMatch(/NaN|Infinity|undefined/);
    }
  });

  it("removes previous trend points when a refresh returns no records", () => {
    const chart = render(buildTrendOption(monthly));
    chart.setOption(buildTrendOption([]));

    for (const series of chart.getModel().getSeries()) expect(series.getData().count()).toBe(0);
    const svg = chart.renderToSVGString();
    expect(svg).not.toContain("12月");
    expect(svg).not.toContain("01月");
  });

  it("clears previous audit sectors and updates the center total on refresh", () => {
    const chart = render(buildAuditOption([{ name: "APPROVED", value: 9 }], 9));
    chart.setOption(buildAuditOption([], 0));

    expect(chart.getModel().getSeriesByIndex(0).getData().count()).toBe(0);
    const svg = chart.renderToSVGString();
    expect(svg).toMatch(/>0<\/text>/);
    expect(svg).not.toContain("审核通过");
  });
});
