import { useEffect, useState } from 'react';
import { 
  App as AntdApp,
  Card, 
  Table, 
  Tag, 
  Tooltip, 
  Space, 
  Input, 
  Button, 
  Select,
  DatePicker,
  Avatar,
  Typography,
  Popconfirm,
  Modal,
  Descriptions
} from 'antd';
import { 
  SearchOutlined, 
  DeleteOutlined, 
  EyeOutlined,
  ExportOutlined,
  FilterOutlined,
  UserOutlined
} from '@ant-design/icons';
import apiClient from '../api/client';
import { API_BASE_URL } from '../config';
import { exportToCSV, exportToJSON } from '../utils/export';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;
const { Text } = Typography;

type Report = {
  _id: string;
  type: 'user' | 'post' | 'message' | 'group';
  reason: string;
  description?: string;
  status?: 'pending' | 'resolved' | 'dismissed';
  createdAt: string;
  sender?: { _id: string; username: string; email?: string; avatar?: string };
  target?: { _id: string; username: string; email?: string; avatar?: string };
};

export default function Reports() {
  const { message: messageApi } = AntdApp.useApp();
  const [reports, setReports] = useState<Report[]>([]);
  const [filteredReports, setFilteredReports] = useState<Report[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);
  const [viewModal, setViewModal] = useState<Report | null>(null);

  const resolveAvatarUrl = (avatar?: string) => {
    if (!avatar) return undefined;
    if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar;
    const base = API_BASE_URL.replace(/\/$/, '');
    return `${base}${avatar}`;
  };

  const fetchReports = async () => {
    try {
      setLoading(true);
      const res = await apiClient.get('/reports');
      setReports(res.data?.data?.reports || []);
      applyFilters(res.data?.data?.reports || [], searchTerm, typeFilter, statusFilter, dateRange);
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Failed to load reports');
      messageApi.error(e?.response?.data?.message || 'Failed to load reports');
    } finally {
      setLoading(false);
    }
  };

  const applyFilters = (
    data: Report[], 
    search: string, 
    type: string, 
    status: string,
    date: [dayjs.Dayjs, dayjs.Dayjs] | null
  ) => {
    let filtered = [...data];

    // Search filter
    if (search.trim()) {
      filtered = filtered.filter(report => 
        report.sender?._id?.includes(search) || 
        report.target?._id?.includes(search) ||
        report.sender?.username?.toLowerCase().includes(search.toLowerCase()) ||
        report.target?.username?.toLowerCase().includes(search.toLowerCase()) ||
        report.reason?.toLowerCase().includes(search.toLowerCase()) ||
        report.description?.toLowerCase().includes(search.toLowerCase())
      );
    }

    // Type filter
    if (type !== 'all') {
      filtered = filtered.filter(report => report.type === type);
    }

    // Status filter
    if (status !== 'all') {
      filtered = filtered.filter(report => report.status === status);
    }

    // Date range filter
    if (date && date[0] && date[1]) {
      filtered = filtered.filter(report => {
        const reportDate = dayjs(report.createdAt);
        return reportDate.isAfter(date[0].startOf('day')) && reportDate.isBefore(date[1].endOf('day'));
      });
    }

    setFilteredReports(filtered);
  };

  const handleSearch = (value: string) => {
    setSearchTerm(value);
    applyFilters(reports, value, typeFilter, statusFilter, dateRange);
  };

  const handleTypeFilter = (value: string) => {
    setTypeFilter(value);
    applyFilters(reports, searchTerm, value, statusFilter, dateRange);
  };

  const handleStatusFilter = (value: string) => {
    setStatusFilter(value);
    applyFilters(reports, searchTerm, typeFilter, value, dateRange);
  };

  const handleDateRangeChange = (dates: any) => {
    setDateRange(dates);
    applyFilters(reports, searchTerm, typeFilter, statusFilter, dates);
  };

  const deleteReport = async (id: string) => {
    try {
      await apiClient.delete(`/reports/${id}`);
      setReports(prev => prev.filter(r => r._id !== id));
      setFilteredReports(prev => prev.filter(r => r._id !== id));
      messageApi.success('Report deleted successfully');
    } catch (e: any) {
      messageApi.error(e?.response?.data?.message || 'Failed to delete report');
    }
  };

  const handleExport = (format: 'csv' | 'json') => {
    const exportData = filteredReports.map(report => ({
      ID: report._id,
      Type: report.type,
      Reason: report.reason,
      Description: report.description || '',
      Status: report.status || 'pending',
      'Sender Username': report.sender?.username || '',
      'Sender ID': report.sender?._id || '',
      'Target Username': report.target?.username || '',
      'Target ID': report.target?._id || '',
      'Created At': dayjs(report.createdAt).format('YYYY-MM-DD HH:mm:ss'),
    }));

    if (format === 'csv') {
      exportToCSV(exportData, 'reports', Object.keys(exportData[0] || {}));
    } else {
      exportToJSON(exportData, 'reports');
    }
    messageApi.success(`Reports exported as ${format.toUpperCase()}`);
  };

  useEffect(() => {
    fetchReports();
  }, []);

  const columns = [
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type: string) => {
        const colors: Record<string, string> = {
          user: 'red',
          post: 'blue',
          message: 'green',
          group: 'orange',
        };
        return <Tag color={colors[type] || 'default'}>{type?.toUpperCase()}</Tag>;
      },
      filters: [
        { text: 'User', value: 'user' },
        { text: 'Post', value: 'post' },
        { text: 'Message', value: 'message' },
        { text: 'Group', value: 'group' },
      ],
      onFilter: (value: any, record: Report) => record.type === value,
    },
    {
      title: 'Sender',
      key: 'sender',
      width: 150,
      render: (_: any, record: Report) => (
        <Space>
          <Avatar 
            size="small" 
            src={resolveAvatarUrl(record.sender?.avatar)} 
            icon={<UserOutlined />}
          />
          <div>
            <Text strong>{record.sender?.username || '-'}</Text>
            <br />
            <Tooltip title={record.sender?._id}>
              <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
                {record.sender?._id ? `${record.sender._id.substring(0, 8)}...` : '-'}
              </Text>
            </Tooltip>
          </div>
        </Space>
      ),
    },
    {
      title: 'Target',
      key: 'target',
      width: 150,
      render: (_: any, record: Report) => (
        <Space>
          <Avatar 
            size="small" 
            src={resolveAvatarUrl(record.target?.avatar)} 
            icon={<UserOutlined />}
          />
          <div>
            <Text strong>{record.target?.username || '-'}</Text>
            <br />
            <Tooltip title={record.target?._id}>
              <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
                {record.target?._id ? `${record.target._id.substring(0, 8)}...` : '-'}
              </Text>
            </Tooltip>
          </div>
        </Space>
      ),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      width: 150,
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const colors: Record<string, string> = {
          pending: 'orange',
          resolved: 'green',
          dismissed: 'default',
        };
        return <Tag color={colors[status] || 'default'}>{status?.toUpperCase() || 'PENDING'}</Tag>;
      },
      filters: [
        { text: 'Pending', value: 'pending' },
        { text: 'Resolved', value: 'resolved' },
        { text: 'Dismissed', value: 'dismissed' },
      ],
      onFilter: (value: any, record: Report) => (record.status || 'pending') === value,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (date: string) => dayjs(date).format('DD/MM/YYYY HH:mm'),
      sorter: (a: Report, b: Report) => dayjs(a.createdAt).unix() - dayjs(b.createdAt).unix(),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 120,
      fixed: 'right' as const,
      render: (_: any, record: Report) => (
        <Space>
          <Tooltip title="View Details">
            <Button 
              type="link" 
              icon={<EyeOutlined />} 
              onClick={() => setViewModal(record)}
            />
          </Tooltip>
          <Popconfirm
            title="Delete this report?"
            onConfirm={() => deleteReport(record._id)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  if (error && !loading) {
    return (
      <Card>
        <div style={{ color: 'red', textAlign: 'center', padding: 50 }}>
          {error}
        </div>
      </Card>
    );
  }

  return (
    <Card 
      title="Reports Management" 
      extra={
        <Space>
          <Button 
            icon={<ExportOutlined />} 
            onClick={() => handleExport('csv')}
          >
            Export CSV
          </Button>
          <Button 
            icon={<ExportOutlined />} 
            onClick={() => handleExport('json')}
          >
            Export JSON
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }} size="large">
        <Space wrap>
          <Input.Search
            placeholder="Search by sender, target, reason, or description"
            allowClear
            onSearch={handleSearch}
            style={{ width: 300 }}
            prefix={<SearchOutlined />}
          />
          <Select
            placeholder="Filter by type"
            value={typeFilter}
            onChange={handleTypeFilter}
            style={{ width: 150 }}
            suffixIcon={<FilterOutlined />}
          >
            <Select.Option value="all">All Types</Select.Option>
            <Select.Option value="user">User</Select.Option>
            <Select.Option value="post">Post</Select.Option>
            <Select.Option value="message">Message</Select.Option>
            <Select.Option value="group">Group</Select.Option>
          </Select>
          <Select
            placeholder="Filter by status"
            value={statusFilter}
            onChange={handleStatusFilter}
            style={{ width: 150 }}
          >
            <Select.Option value="all">All Status</Select.Option>
            <Select.Option value="pending">Pending</Select.Option>
            <Select.Option value="resolved">Resolved</Select.Option>
            <Select.Option value="dismissed">Dismissed</Select.Option>
          </Select>
          <RangePicker
            placeholder={['Start Date', 'End Date']}
            onChange={handleDateRangeChange}
            allowClear
          />
        </Space>
      </Space>

      <Table
        columns={columns}
        dataSource={filteredReports}
        rowKey="_id"
        loading={loading}
        scroll={{ x: 1200 }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} reports`,
        }}
        locale={{ emptyText: 'No reports found' }}
      />

      <Modal
        title="Report Details"
        open={!!viewModal}
        onCancel={() => setViewModal(null)}
        footer={[
          <Button key="close" onClick={() => setViewModal(null)}>
            Close
          </Button>,
          <Popconfirm
            key="delete"
            title="Delete this report?"
            onConfirm={() => {
              if (viewModal) {
                deleteReport(viewModal._id);
                setViewModal(null);
              }
            }}
          >
            <Button danger icon={<DeleteOutlined />}>
              Delete
            </Button>
          </Popconfirm>,
        ]}
        width={600}
      >
        {viewModal && (
          <Descriptions bordered column={1}>
            <Descriptions.Item label="Type">
              <Tag color={viewModal.type === 'user' ? 'red' : viewModal.type === 'post' ? 'blue' : 'green'}>
                {viewModal.type?.toUpperCase()}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Reason">{viewModal.reason}</Descriptions.Item>
            <Descriptions.Item label="Description">
              {viewModal.description || <Text type="secondary">No description</Text>}
            </Descriptions.Item>
            <Descriptions.Item label="Status">
              <Tag color={viewModal.status === 'pending' ? 'orange' : viewModal.status === 'resolved' ? 'green' : 'default'}>
                {viewModal.status?.toUpperCase() || 'PENDING'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Sender">
              <Space>
                <Avatar src={resolveAvatarUrl(viewModal.sender?.avatar)} icon={<UserOutlined />} />
                <div>
                  <Text strong>{viewModal.sender?.username || '-'}</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {viewModal.sender?._id || '-'}
                  </Text>
                </div>
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="Target">
              <Space>
                <Avatar src={resolveAvatarUrl(viewModal.target?.avatar)} icon={<UserOutlined />} />
                <div>
                  <Text strong>{viewModal.target?.username || '-'}</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {viewModal.target?._id || '-'}
                  </Text>
                </div>
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="Created At">
              {dayjs(viewModal.createdAt).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </Card>
  );
}
