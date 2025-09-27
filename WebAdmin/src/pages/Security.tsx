import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Space, Typography, Alert, Tabs, Avatar } from 'antd';
import { 
  UserDeleteOutlined, 
  BlockOutlined, 
  UnlockOutlined,
  EyeOutlined
} from '@ant-design/icons';
import apiClient from '../api/client';

const { Title, Text } = Typography;
const { Option } = Select;

interface BlockedUser {
  _id: string;
  username: string;
  email: string;
  reason: string;
  blockedAt: string;
  blockedBy: string;
}

interface BlockedIP {
  _id: string;
  ip: string;
  reason: string;
  blockedAt: string;
  blockedBy: string;
}

interface AuditLog {
  _id: string;
  user: string;
  action: string;
  resource: string;
  details: string;
  timestamp: string;
  ipAddress: string;
}

export default function Security() {
  const [blockedUsers, setBlockedUsers] = useState<BlockedUser[]>([]);
  const [blockedIPs, setBlockedIPs] = useState<BlockedIP[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [blockUserModal, setBlockUserModal] = useState(false);
  const [blockIPModal, setBlockIPModal] = useState(false);
  const [form] = Form.useForm();
  const [ipForm] = Form.useForm();

  useEffect(() => {
    fetchSecurityData();
  }, []);

  const fetchSecurityData = async () => {
    try {
      setLoading(true);
      const [usersRes, ipsRes, logsRes] = await Promise.all([
        apiClient.get('/security/blocked-users'),
        apiClient.get('/security/blocked-ips'),
        apiClient.get('/security/audit-logs')
      ]);

      setBlockedUsers(usersRes.data.data);
      setBlockedIPs(ipsRes.data.data);
      setAuditLogs(logsRes.data.data);
    } catch (error) {
      console.error('Failed to fetch security data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleBlockUser = async (values: any) => {
    try {
      await apiClient.post('/security/block-user', values);
      setBlockUserModal(false);
      form.resetFields();
      fetchSecurityData();
    } catch (error) {
      console.error('Failed to block user:', error);
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

  const handleUnblockUser = async (userId: string) => {
    try {
      await apiClient.delete(`/security/blocked-users/${userId}`);
      fetchSecurityData();
    } catch (error) {
      console.error('Failed to unblock user:', error);
    }
  };

  const handleUnblockIP = async (ipId: string) => {
    try {
      await apiClient.delete(`/security/blocked-ips/${ipId}`);
      fetchSecurityData();
    } catch (error) {
      console.error('Failed to unblock IP:', error);
    }
  };

  const blockedUserColumns = [
    { title: 'Username', dataIndex: 'username', key: 'username' },
    { title: 'Email', dataIndex: 'email', key: 'email' },
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
      render: (_: any, record: BlockedUser) => (
        <Button
          type="primary"
          icon={<UnlockOutlined />}
          onClick={() => handleUnblockUser(record._id)}
        >
          Unblock
        </Button>
      )
    }
  ];

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
      render: (user: string) => (
        <Space>
          <Avatar size="small">{user?.[0]?.toUpperCase()}</Avatar>
          <Text>{user}</Text>
        </Space>
      )
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
    }
  ];

  const tabItems = [
    {
      key: 'blocked-users',
      label: (
        <span>
          <UserDeleteOutlined />
          Blocked Users
        </span>
      ),
      children: (
        <div>
          <div style={{ marginBottom: 16 }}>
            <Button 
              type="primary" 
              icon={<BlockOutlined />}
              onClick={() => setBlockUserModal(true)}
            >
              Block User
            </Button>
          </div>
          <Table
            dataSource={blockedUsers}
            columns={blockedUserColumns}
            rowKey="_id"
            loading={loading}
            pagination={{ pageSize: 10 }}
          />
        </div>
      )
    },
    {
      key: 'blocked-ips',
      label: (
        <span>
          <BlockOutlined />
          Blocked IPs
        </span>
      ),
      children: (
        <div>
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
        <span>
          <EyeOutlined />
          Audit Logs
        </span>
      ),
      children: (
        <Table
          dataSource={auditLogs}
          columns={auditLogColumns}
          rowKey="_id"
          loading={loading}
          pagination={{ pageSize: 20 }}
          scroll={{ x: 800 }}
        />
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

      <Tabs defaultActiveKey="blocked-users" items={tabItems} />

      {/* Block User Modal */}
      <Modal
        title="Block User"
        open={blockUserModal}
        onCancel={() => {
          setBlockUserModal(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
      >
        <Form form={form} onFinish={handleBlockUser} layout="vertical">
          <Form.Item
            name="userId"
            label="User ID"
            rules={[{ required: true, message: 'Please enter user ID' }]}
          >
            <Input placeholder="Enter user ID to block" />
          </Form.Item>
          
          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, message: 'Please enter reason' }]}
          >
            <Select placeholder="Select reason">
              <Option value="spam">Spam</Option>
              <Option value="abuse">Abuse</Option>
              <Option value="violation">Terms Violation</Option>
              <Option value="suspicious">Suspicious Activity</Option>
              <Option value="other">Other</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

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
