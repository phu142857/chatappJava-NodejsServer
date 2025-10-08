import { useEffect, useState } from 'react';
import { Input, Space, Tag, Tooltip } from 'antd';
import apiClient from '../api/client';

type Report = {
  _id: string;
  content: string;
  createdAt: string;
  sender?: { _id: string; username: string; email?: string };
  target?: { _id: string; username: string; email?: string };
};

export default function Reports() {
  const [reports, setReports] = useState<Report[]>([]);
  const [filteredReports, setFilteredReports] = useState<Report[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [searchTerm, setSearchTerm] = useState<string>('');

  const fetchReports = async () => {
    try {
      setLoading(true);
      const res = await apiClient.get('/reports');
      setReports(res.data?.data?.reports || []);
      setFilteredReports(res.data?.data?.reports || []);
    } catch (e: any) {
      setError(e?.message || 'Failed to load reports');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (value: string) => {
    setSearchTerm(value);
    if (!value.trim()) {
      setFilteredReports(reports);
      return;
    }
    
    const filtered = reports.filter(report => 
      report.sender?._id?.includes(value) || 
      report.target?._id?.includes(value) ||
      report.sender?.username?.toLowerCase().includes(value.toLowerCase()) ||
      report.target?.username?.toLowerCase().includes(value.toLowerCase())
    );
    setFilteredReports(filtered);
  };

  const deleteReport = async (id: string) => {
    try {
      await apiClient.delete(`/reports/${id}`);
      setReports(prev => prev.filter(r => r._id !== id));
      setFilteredReports(prev => prev.filter(r => r._id !== id));
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
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="Search by sender/target UUID or username"
          allowClear
          onSearch={handleSearch}
          style={{ width: 400 }}
        />
      </Space>
      {filteredReports.length === 0 ? (
        <div>No reports{searchTerm ? ' found' : ''}</div>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Sender</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Sender UUID</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Target</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Target UUID</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Content</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Created</th>
              <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: 8 }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredReports.map(r => (
              <tr key={r._id}>
                <td style={{ padding: 8 }}>{r.sender?.username || '-'}</td>
                <td style={{ padding: 8 }}>
                  <Tooltip title={r.sender?._id}>
                    <Tag style={{ fontFamily: 'monospace', fontSize: '12px' }}>
                      {r.sender?._id ? `${r.sender._id.substring(0, 8)}...` : '-'}
                    </Tag>
                  </Tooltip>
                </td>
                <td style={{ padding: 8 }}>{r.target?.username || '-'}</td>
                <td style={{ padding: 8 }}>
                  <Tooltip title={r.target?._id}>
                    <Tag style={{ fontFamily: 'monospace', fontSize: '12px' }}>
                      {r.target?._id ? `${r.target._id.substring(0, 8)}...` : '-'}
                    </Tag>
                  </Tooltip>
                </td>
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


