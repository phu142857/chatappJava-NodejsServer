import { useEffect, useState } from 'react';
import apiClient from '../api/client';

type Report = {
  _id: string;
  content: string;
  createdAt: string;
  sender?: { username: string; email?: string };
  target?: { username: string; email?: string };
};

export default function Reports() {
  const [reports, setReports] = useState<Report[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');

  const fetchReports = async () => {
    try {
      setLoading(true);
      const res = await apiClient.get('/reports');
      setReports(res.data?.data?.reports || []);
    } catch (e: any) {
      setError(e?.message || 'Failed to load reports');
    } finally {
      setLoading(false);
    }
  };

  const deleteReport = async (id: string) => {
    try {
      await apiClient.delete(`/reports/${id}`);
      setReports(prev => prev.filter(r => r._id !== id));
    } catch (e: any) {
      alert(e?.message || 'Failed to delete report');
    }
  };

  useEffect(() => {
    fetchReports();
  }, []);

  if (loading) return <div>Loading...</div>;
  if (error) return <div style={{ color: 'red' }}>{error}</div>;

  return (
    <div style={{ padding: 16 }}>
      <h2>Reports</h2>
      {reports.length === 0 ? (
        <div>No reports</div>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Sender</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Target</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Content</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Created</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {reports.map(r => (
              <tr key={r._id}>
                <td style={{ padding: 8 }}>{r.sender?.username || '-'}</td>
                <td style={{ padding: 8 }}>{r.target?.username || '-'}</td>
                <td style={{ padding: 8, whiteSpace: 'pre-wrap' }}>{r.content}</td>
                <td style={{ padding: 8 }}>{new Date(r.createdAt).toLocaleString()}</td>
                <td style={{ padding: 8 }}>
                  <button onClick={() => deleteReport(r._id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}


