import dayjs from 'dayjs';

export const exportToCSV = (data: any[], filename: string, headers: string[] = []) => {
  if (data.length === 0) {
    return;
  }

  // Auto-generate headers from first object if not provided
  const csvHeaders = headers.length > 0 
    ? headers 
    : Object.keys(data[0]);

  const csv = [
    csvHeaders.join(','),
    ...data.map(row => 
      csvHeaders.map(header => {
        const value = row[header] || '';
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (typeof value === 'string' && (value.includes(',') || value.includes('"') || value.includes('\n'))) {
          return `"${value.replace(/"/g, '""')}"`;
        }
        return value;
      }).join(',')
    )
  ].join('\n');

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${filename}_${dayjs().format('YYYY-MM-DD_HH-mm-ss')}.csv`;
  link.click();
  window.URL.revokeObjectURL(url);
};

export const exportToJSON = (data: any[], filename: string) => {
  const json = JSON.stringify(data, null, 2);
  const blob = new Blob([json], { type: 'application/json' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${filename}_${dayjs().format('YYYY-MM-DD_HH-mm-ss')}.json`;
  link.click();
  window.URL.revokeObjectURL(url);
};

