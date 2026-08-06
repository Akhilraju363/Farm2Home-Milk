import axiosClient from './axiosClient'

const BASE = '/reports'

// `path` is the URL segment (plural, e.g. /reports/customers/export); `filePrefix` is the exact
// prefix ReportController's export() helper builds the downloaded filename from (singular, e.g.
// "customer-report") - the two don't share a naming convention, so both must be listed explicitly
// rather than derived from one another.
export const REPORT_TYPES = [
  { path: 'sales', filePrefix: 'sales-report', label: 'Sales Report' },
  { path: 'customers', filePrefix: 'customer-report', label: 'Customer Report' },
  { path: 'subscriptions', filePrefix: 'subscription-report', label: 'Subscription Report' },
  { path: 'inventory', filePrefix: 'inventory-report', label: 'Inventory Report' },
  { path: 'production', filePrefix: 'production-report', label: 'Production Report' },
  { path: 'deliveries', filePrefix: 'delivery-report', label: 'Delivery Report' },
  { path: 'payments', filePrefix: 'payment-report', label: 'Payment Report' },
] as const

export type ReportType = (typeof REPORT_TYPES)[number]
export type ExportFormat = 'CSV' | 'EXCEL' | 'PDF'

const EXTENSIONS: Record<ExportFormat, string> = { CSV: 'csv', EXCEL: 'xlsx', PDF: 'pdf' }

export const reportsService = {
  // responseType 'blob' is required for binary file downloads - a plain JSON parse would
  // corrupt CSV/Excel/PDF bytes. Filename mirrors the backend's own pattern exactly (see
  // ReportController's export()->buildFilename) rather than reading it back off
  // Content-Disposition, which isn't reliably CORS-exposed.
  export: async (report: ReportType, format: ExportFormat = 'CSV') => {
    const res = await axiosClient.get(`${BASE}/${report.path}/export`, {
      params: { format },
      responseType: 'blob',
    })
    const today = new Date().toISOString().slice(0, 10)
    return { blob: res.data as Blob, filename: `${report.filePrefix}-${today}.${EXTENSIONS[format]}` }
  },
}
