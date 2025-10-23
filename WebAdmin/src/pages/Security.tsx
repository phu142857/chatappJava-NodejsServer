import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Space, Typography, Alert, Tabs, Avatar, Popconfirm, message } from 'antd';
import { 
  BlockOutlined, 
  UnlockOutlined,
  EyeOutlined,
  DeleteOutlined,
  ClearOutlined
} from '@ant-design/icons';
import apiClient from '../api/client';

const { Title, Text } = Typography;
const { Option } = Select;

interface BlockedIP {
  _id: string;
  ip: string;
  reason: string;
  blockedAt: string;
  blockedBy: string;
}

interface AuditLog {
  _id: string;
  user: string | { _id: string; username: string };
  action: string;
  resource: string;
  details: string;
  timestamp: string;
  ipAddress: string;
}

export default function Security() {
  const [blockedIPs, setBlockedIPs] = useState<BlockedIP[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [blockIPModal, setBlockIPModal] = useState(false);
  const [ipForm] = Form.useForm();

  useEffect(() => {
    fetchSecurityData();
  }, []);

  const fetchSecurityData = async () => {
    try {
      setLoading(true);
      const [ipsRes, logsRes] = await Promise.all([
        apiClient.get('/security/blocked-ips'),
        apiClient.get('/security/audit-logs')
      ]);

      setBlockedIPs(ipsRes.data.data);
      setAuditLogs(logsRes.data.data);
    } catch (error) {
      console.error('Failed to fetch security data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleBlockIP = async (values: any) => {
    try {
      await apiClient.post('/security/block-ip', values);
      setBlockIPModal(false);
      ipForm.resetFields();
      fetchSecurityData();
    } catch (error) {
      console.error('Failed to block IP:', error);
    }
  };

  const handleUnblockIP = async (ipId: string) => {
    try {
      await apiClient.delete(`/security/blocked-ips/${ipId}`);
      message.success('IP address unblocked successfully');
      fetchSecurityData();
    } catch (error) {
      console.error('Failed to unblock IP:', error);
      message.error('Failed to unblock IP address');
    }
  };

  const handleDeleteAuditLog = async (logId: string) => {
    try {
      await apiClient.delete(`/security/audit-logs/${logId}`);
      message.success('Audit log deleted successfully');
      fetchSecurityData();
    } catch (error) {
      console.error('Failed to delete audit log:', error);
      message.error('Failed to delete audit log');
    }
  };

  const handleDeleteAllAuditLogs = async () => {
    try {
      await apiClient.delete('/security/audit-logs');
      message.success('All audit logs deleted successfully');
      fetchSecurityData();
    } catch (error) {
      console.error('Failed to delete all audit logs:', error);
      message.error('Failed to delete all audit logs');
    }
  };


  const blockedIPColumns = [
    { title: 'IP Address', dataIndex: 'ip', key: 'ip' },
    { title: 'Reason', dataIndex: 'reason', key: 'reason' },
    { 
      title: 'Blocked At', 
      dataIndex: 'blockedAt', 
      key: 'blockedAt',
      render: (date: string) => new Date(date).toLocaleString()
    },
    { title: 'Blocked By', dataIndex: 'blockedBy', key: 'blockedBy' },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: any, record: BlockedIP) => (
        <Button
          key={`unblock-ip-${record._id}`}
          type="primary"
          icon={<UnlockOutlined />}
          onClick={() => handleUnblockIP(record._id)}
        >
          Unblock
        </Button>
      )
    }
  ];

  const auditLogColumns = [
    { 
      title: 'User', 
      dataIndex: 'user', 
      key: 'user',
      render: (user: string | { _id: string; username: string }) => {
        const username = typeof user === 'string' ? user : user?.username || 'Unknown';
        return (
          <Space key={`user-${username}`}>
            <Avatar size="small">{username?.[0]?.toUpperCase()}</Avatar>
            <Text>{username}</Text>
          </Space>
        );
      }
    },
    { title: 'Action', dataIndex: 'action', key: 'action' },
    { title: 'Resource', dataIndex: 'resource', key: 'resource' },
    { title: 'Details', dataIndex: 'details', key: 'details' },
    { 
      title: 'IP Address', 
      dataIndex: 'ipAddress', 
      key: 'ipAddress' 
    },
    { 
      title: 'Timestamp', 
      dataIndex: 'timestamp', 
      key: 'timestamp',
      render: (date: string) => new Date(date).toLocaleString()
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: any, record: AuditLog) => (
        <Popconfirm
          title="Delete this audit log?"
          description="This action cannot be undone."
          onConfirm={() => handleDeleteAuditLog(record._id)}
          okText="Yes, delete"
          cancelText="Cancel"
        >
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            size="small"
          >
            Delete
          </Button>
        </Popconfirm>
      )
    }
  ];

  const tabItems = [
    {
      key: 'blocked-ips',
      label: (
        <span key="blocked-ips-label">
          <BlockOutlined />
          Blocked IPs
        </span>
      ),
      children: (
        <div key="blocked-ips-content">
          <div style={{ marginBottom: 16 }}>
            <Button 
              type="primary" 
              icon={<BlockOutlined />}
              onClick={() => setBlockIPModal(true)}
            >
              Block IP
            </Button>
          </div>
          <Table
            dataSource={blockedIPs}
            columns={blockedIPColumns}
            rowKey="_id"
            loading={loading}
            pagination={{ pageSize: 10 }}
          />
        </div>
      )
    },
    {
      key: 'audit-logs',
      label: (
        <span key="audit-logs-label">
          <EyeOutlined />
          Audit Logs
        </span>
      ),
      children: (
        <div key="audit-logs-content">
          <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text type="secondary">
              Total logs: {auditLogs.length}
            </Text>
            <Popconfirm
              title="Delete all audit logs?"
              description="This will permanently delete all audit logs. This action cannot be undone."
              onConfirm={handleDeleteAllAuditLogs}
              okText="Yes, delete all"
              cancelText="Cancel"
            >
              <Button
                type="primary"
                danger
                icon={<ClearOutlined />}
              >
                Delete All Logs
              </Button>
            </Popconfirm>
          </div>
          <Table
            dataSource={auditLogs}
            columns={auditLogColumns}
            rowKey="_id"
            loading={loading}
            pagination={{ pageSize: 20 }}
            scroll={{ x: 800 }}
          />
        </div>
      )
    }
  ];

  return (
    <div>
      <Title level={2}>Security Management</Title>
      
      <Alert
        message="Security Center"
        description="Manage blocked users, IP addresses, and view audit logs for security monitoring."
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Tabs defaultActiveKey="blocked-ips" items={tabItems} />

      {/* Block IP Modal */}
      <Modal
        title="Block IP Address"
        open={blockIPModal}
        onCancel={() => {
          setBlockIPModal(false);
          ipForm.resetFields();
        }}
        onOk={() => ipForm.submit()}
      >
        <Form form={ipForm} onFinish={handleBlockIP} layout="vertical">
          <Form.Item
            name="ip"
            label="IP Address"
            rules={[
              { required: true, message: 'Please enter IP address' },
              { pattern: /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/, message: 'Invalid IP address' }
            ]}
          >
            <Input placeholder="Enter IP address to block" />
          </Form.Item>
          
          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, message: 'Please enter reason' }]}
          >
            <Select placeholder="Select reason">
              <Option value="brute_force">Brute Force Attack</Option>
              <Option value="spam">Spam</Option>
              <Option value="abuse">Abuse</Option>
              <Option value="suspicious">Suspicious Activity</Option>
              <Option value="other">Other</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
