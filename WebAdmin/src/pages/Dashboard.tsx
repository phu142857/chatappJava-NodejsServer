import { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Progress, Table, Tag, Space, Typography, Alert, Spin } from 'antd';
import { 
  DesktopOutlined, 
  UserOutlined, 
  MessageOutlined, 
  PhoneOutlined,
  DatabaseOutlined,
  WifiOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons';
import apiClient from '../api/client';

const { Title, Text } = Typography;

interface ServerStats {
  users: {
    total: number;
    active: number;
    online: number;
  };
  messages: {
    total: number;
    today: number;
    thisWeek: number;
  };
  calls: {
    total: number;
    today: number;
    successful: number;
    failed: number;
  };
  server: {
    cpu: number;
    memory: number;
    disk: number;
    uptime: string;
  };
  services: {
    auth: boolean;
    chat: boolean;
    videoCall: boolean;
    websocket: boolean;
  };
}

export default function Dashboard() {
  const [stats, setStats] = useState<ServerStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchStats();
    // Refresh every 30 seconds
    const interval = setInterval(fetchStats, 30000);
    return () => clearInterval(interval);
  }, []);

  const fetchStats = async () => {
    try {
      setLoading(true);
      setError(null);
      
      // Fetch all statistics in parallel
      const [usersRes, messagesRes, callsRes, serverRes] = await Promise.all([
        apiClient.get('/users/stats'),
        apiClient.get('/messages/stats'),
        apiClient.get('/calls/stats'),
        apiClient.get('/server/health')
      ]);

      setStats({
        users: usersRes.data.data,
        messages: messagesRes.data.data,
        calls: callsRes.data.data,
        server: serverRes.data.data,
        services: serverRes.data.data.services
      });
    } catch (err) {
      console.error('Failed to fetch stats:', err);
      setError('Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  if (loading && !stats) {
    return (
      <div style={{ textAlign: 'center', padding: 50 }}>
        <Spin size="large" />
        <div style={{ marginTop: 16 }}>Loading dashboard...</div>
      </div>
    );
  }

  if (error) {
    return (
      <Alert
        message="Error"
        description={error}
        type="error"
        showIcon
        action={
          <button onClick={fetchStats} style={{ marginLeft: 16 }}>
            Retry
          </button>
        }
      />
    );
  }

  const serviceStatus = [
    { name: 'Authentication', status: stats?.services.auth, icon: <UserOutlined /> },
    { name: 'Chat Service', status: stats?.services.chat, icon: <MessageOutlined /> },
    { name: 'Video Call', status: stats?.services.videoCall, icon: <PhoneOutlined /> },
    { name: 'WebSocket', status: stats?.services.websocket, icon: <WifiOutlined /> }
  ];

  return (
    <div>
      <Title level={2}>Server Dashboard</Title>
      
      {/* Server Health Overview */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="CPU Usage"
              value={stats?.server.cpu || 0}
              suffix="%"
              prefix={<DesktopOutlined />}
              valueStyle={{ color: (stats?.server.cpu || 0) > 80 ? '#cf1322' : '#3f8600' }}
            />
            <Progress 
              percent={stats?.server.cpu || 0} 
              size="small" 
              status={(stats?.server.cpu || 0) > 80 ? 'exception' : 'normal'}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="Memory Usage"
              value={stats?.server.memory || 0}
              suffix="%"
              prefix={<DatabaseOutlined />}
              valueStyle={{ color: (stats?.server.memory || 0) > 80 ? '#cf1322' : '#3f8600' }}
            />
            <Progress 
              percent={stats?.server.memory || 0} 
              size="small" 
              status={(stats?.server.memory || 0) > 80 ? 'exception' : 'normal'}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="Disk Usage"
              value={stats?.server.disk || 0}
              suffix="%"
              prefix={<DatabaseOutlined />}
              valueStyle={{ color: (stats?.server.disk || 0) > 80 ? '#cf1322' : '#3f8600' }}
            />
            <Progress 
              percent={stats?.server.disk || 0} 
              size="small" 
              status={(stats?.server.disk || 0) > 80 ? 'exception' : 'normal'}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="Uptime"
              value={stats?.server.uptime || '0d 0h 0m'}
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* User Statistics */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Total Users"
              value={stats?.users.total || 0}
              prefix={<UserOutlined />}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Active Users"
              value={stats?.users.active || 0}
              prefix={<UserOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Online Now"
              value={stats?.users.online || 0}
              prefix={<UserOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Message & Call Statistics */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12}>
          <Card title="Message Statistics">
            <Row gutter={16}>
              <Col span={8}>
                <Statistic
                  title="Total Messages"
                  value={stats?.messages.total || 0}
                  prefix={<MessageOutlined />}
                />
              </Col>
              <Col span={8}>
                <Statistic
                  title="Today"
                  value={stats?.messages.today || 0}
                  prefix={<MessageOutlined />}
                />
              </Col>
              <Col span={8}>
                <Statistic
                  title="This Week"
                  value={stats?.messages.thisWeek || 0}
                  prefix={<MessageOutlined />}
                />
              </Col>
            </Row>
          </Card>
        </Col>
        
        <Col xs={24} sm={12}>
          <Card title="Call Statistics">
            <Row gutter={16}>
              <Col span={8}>
                <Statistic
                  title="Total Calls"
                  value={stats?.calls.total || 0}
                  prefix={<PhoneOutlined />}
                />
              </Col>
              <Col span={8}>
                <Statistic
                  title="Today"
                  value={stats?.calls.today || 0}
                  prefix={<PhoneOutlined />}
                />
              </Col>
              <Col span={8}>
                <Statistic
                  title="Success Rate"
                  value={(stats?.calls.total || 0) > 0 ? Math.round(((stats?.calls.successful || 0) / (stats?.calls.total || 1)) * 100) : 0}
                  suffix="%"
                  prefix={<CheckCircleOutlined />}
                  valueStyle={{ color: '#3f8600' }}
                />
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

      {/* Service Status */}
      <Card title="Service Status" style={{ marginBottom: 24 }}>
        <Row gutter={[16, 16]}>
          {serviceStatus.map((service, index) => (
            <Col xs={24} sm={12} md={6} key={index}>
              <Card size="small">
                <Space>
                  {service.icon}
                  <Text strong>{service.name}</Text>
                  <Tag 
                    color={service.status ? 'success' : 'error'}
                    icon={service.status ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
                  >
                    {service.status ? 'Online' : 'Offline'}
                  </Tag>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      </Card>

      {/* Recent Activity */}
      <Card title="Recent Activity">
        <Table
          size="small"
          dataSource={[]}
          columns={[
            { title: 'Time', dataIndex: 'time', width: 150 },
            { title: 'User', dataIndex: 'user', width: 120 },
            { title: 'Action', dataIndex: 'action', width: 200 },
            { title: 'Details', dataIndex: 'details' }
          ]}
          pagination={false}
          locale={{ emptyText: 'No recent activity' }}
        />
      </Card>
    </div>
  );
}