import { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Table, DatePicker, Select, Space, Typography, Progress, Avatar } from 'antd';
import { Line, Pie } from '@ant-design/charts';
import { 
  UserOutlined, 
  MessageOutlined, 
  PhoneOutlined,
  CalendarOutlined,
  RiseOutlined
} from '@ant-design/icons';
import apiClient from '../api/client';
import dayjs from 'dayjs';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;
const { Option } = Select;

interface UserStats {
  total: number;
  active: number;
  newToday: number;
  newThisWeek: number;
  newThisMonth: number;
  newInRange?: number; // New users in selected date range
  byRole: {
    user: number;
    moderator: number;
    admin: number;
  };
}

interface MessageStats {
  total: number;
  today: number;
  thisWeek: number;
  thisMonth: number;
  inRange?: number; // Messages in selected date range
  byType: {
    text: number;
    image: number;
    file: number;
    audio: number;
    video: number;
  };
  hourly: Array<{ hour: number; count: number }>;
}

interface CallStats {
  total: number;
  today: number;
  thisWeek: number;
  thisMonth: number;
  inRange?: number; // Calls in selected date range
  byStatus: {
    completed: number;
    failed: number;
    canceled: number;
  };
  averageDuration: number;
  successRate: number;
}

interface TopUser {
  _id: string;
  username: string;
  messageCount: number;
  callCount: number;
  lastActive: string;
}

export default function Statistics() {
  const [userStats, setUserStats] = useState<UserStats | null>(null);
  const [messageStats, setMessageStats] = useState<MessageStats | null>(null);
  const [callStats, setCallStats] = useState<CallStats | null>(null);
  const [topUsers, setTopUsers] = useState<TopUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(30, 'day'),
    dayjs()
  ]);
  const [period, setPeriod] = useState<string>('30d');

  useEffect(() => {
    fetchStatistics();
  }, [dateRange]);

  const fetchStatistics = async () => {
    if (!dateRange || !dateRange[0] || !dateRange[1]) {
      return;
    }
    
    try {
      setLoading(true);
      
      // Format dates to ensure full day coverage
      const startDate = dateRange[0].startOf('day').toISOString().split('T')[0];
      const endDate = dateRange[1].endOf('day').toISOString().split('T')[0];
      
      console.log('Fetching statistics for range:', { 
        startDate, 
        endDate,
        startDateObj: dateRange[0].format('YYYY-MM-DD'),
        endDateObj: dateRange[1].format('YYYY-MM-DD')
      });
      
      const [usersRes, messagesRes, callsRes, topUsersRes] = await Promise.all([
        apiClient.get(`/statistics/users?startDate=${startDate}&endDate=${endDate}`),
        apiClient.get(`/statistics/messages?startDate=${startDate}&endDate=${endDate}`),
        apiClient.get(`/statistics/calls?startDate=${startDate}&endDate=${endDate}`),
        apiClient.get('/statistics/top-users')
      ]);

      console.log('Statistics response:', {
        users: usersRes.data?.data,
        messages: messagesRes.data?.data,
        calls: callsRes.data?.data
      });

      if (usersRes.data?.data) setUserStats(usersRes.data.data);
      if (messagesRes.data?.data) setMessageStats(messagesRes.data.data);
      if (callsRes.data?.data) setCallStats(callsRes.data.data);
      if (topUsersRes.data?.data) setTopUsers(topUsersRes.data.data);
    } catch (error: any) {
      console.error('Failed to fetch statistics:', error);
      console.error('Error details:', error?.response?.data || error?.message);
    } finally {
      setLoading(false);
    }
  };

  const handlePeriodChange = (value: string) => {
    setPeriod(value);
    const now = dayjs();
    let newDateRange: [dayjs.Dayjs, dayjs.Dayjs];
    
    switch (value) {
      case '7d':
        newDateRange = [now.subtract(7, 'day'), now];
        break;
      case '30d':
        newDateRange = [now.subtract(30, 'day'), now];
        break;
      case '90d':
        newDateRange = [now.subtract(90, 'day'), now];
        break;
      case '1y':
        newDateRange = [now.subtract(1, 'year'), now];
        break;
      default:
        return; // Don't change date range for custom
    }
    
    setDateRange(newDateRange);
  };

  const handleDateRangeChange = (dates: any) => {
    if (dates && dates[0] && dates[1]) {
      const newRange: [dayjs.Dayjs, dayjs.Dayjs] = [dates[0], dates[1]];
      setDateRange(newRange);
      // Check if the selected range matches any preset period
      const now = dayjs();
      const daysDiff = now.diff(dates[0], 'day');
      const isToday = dates[1].isSame(now, 'day');
      
      if (isToday) {
        if (daysDiff === 7) {
          setPeriod('7d');
        } else if (daysDiff === 30) {
          setPeriod('30d');
        } else if (daysDiff === 90) {
          setPeriod('90d');
        } else if (daysDiff === 365 || daysDiff === 366) {
          setPeriod('1y');
        } else {
          setPeriod('custom');
        }
      } else {
        setPeriod('custom');
      }
    }
  };

  const topUsersColumns = [
    {
      title: 'User',
      dataIndex: 'username',
      key: 'username',
      render: (username: string, record: TopUser) => (
        <Space>
          <Avatar size="small" icon={<UserOutlined />} />
          <div>
            <Text strong>{username}</Text>
            <br />
            <Text type="secondary" style={{ fontSize: 12 }}>
              Last active: {dayjs(record.lastActive).format('MMM DD, YYYY')}
            </Text>
          </div>
        </Space>
      )
    },
    {
      title: 'Messages',
      dataIndex: 'messageCount',
      key: 'messageCount',
      render: (count: number) => (
        <Statistic value={count} prefix={<MessageOutlined />} valueStyle={{ fontSize: 16 }} />
      )
    },
    {
      title: 'Calls',
      dataIndex: 'callCount',
      key: 'callCount',
      render: (count: number) => (
        <Statistic value={count} prefix={<PhoneOutlined />} valueStyle={{ fontSize: 16 }} />
      )
    }
  ];

  return (
    <div>
      <Title level={2}>Statistics & Analytics</Title>
      
      {/* Date Range and Period Selection */}
      <Card style={{ marginBottom: 24 }}>
        <Row gutter={16} align="middle">
          <Col>
            <Text strong>Date Range:</Text>
          </Col>
          <Col>
            <RangePicker
              value={dateRange}
              onChange={handleDateRangeChange}
              format="YYYY-MM-DD"
            />
          </Col>
          <Col>
            <Text strong>Quick Select:</Text>
          </Col>
          <Col>
            <Select value={period} onChange={handlePeriodChange} style={{ width: 150 }}>
              <Option value="7d">Last 7 days</Option>
              <Option value="30d">Last 30 days</Option>
              <Option value="90d">Last 90 days</Option>
              <Option value="1y">Last year</Option>
              <Option value="custom">Custom Range</Option>
            </Select>
          </Col>
        </Row>
      </Card>

      {/* User Statistics */}
      <Card title="User Statistics" style={{ marginBottom: 24 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="Total Users"
              value={userStats?.total || 0}
              prefix={<UserOutlined />}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="Active Users"
              value={userStats?.active || 0}
              prefix={<UserOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="New in Selected Range"
              value={userStats?.newInRange || 0}
              prefix={<RiseOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="New This Week"
              value={userStats?.newThisWeek || 0}
              prefix={<RiseOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Col>
        </Row>
        
        {/* User Role Distribution */}
        <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
          <Col xs={24} sm={8}>
            <Card size="small">
              <Statistic
                title="Regular Users"
                value={userStats?.byRole.user || 0}
                suffix={`${userStats ? Math.round((userStats.byRole.user / userStats.total) * 100) : 0}%`}
              />
              <Progress 
                percent={userStats ? Math.round((userStats.byRole.user / userStats.total) * 100) : 0} 
                size="small" 
              />
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card size="small">
              <Statistic
                title="Moderators"
                value={userStats?.byRole.moderator || 0}
                suffix={`${userStats ? Math.round((userStats.byRole.moderator / userStats.total) * 100) : 0}%`}
              />
              <Progress 
                percent={userStats ? Math.round((userStats.byRole.moderator / userStats.total) * 100) : 0} 
                size="small" 
              />
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card size="small">
              <Statistic
                title="Admins"
                value={userStats?.byRole.admin || 0}
                suffix={`${userStats ? Math.round((userStats.byRole.admin / userStats.total) * 100) : 0}%`}
              />
              <Progress 
                percent={userStats ? Math.round((userStats.byRole.admin / userStats.total) * 100) : 0} 
                size="small" 
              />
            </Card>
          </Col>
        </Row>
      </Card>

      {/* Message Statistics */}
      <Card title="Message Statistics" style={{ marginBottom: 24 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="Total Messages"
              value={messageStats?.total || 0}
              prefix={<MessageOutlined />}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="In Selected Range"
              value={messageStats?.inRange || 0}
              prefix={<MessageOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="This Week"
              value={messageStats?.thisWeek || 0}
              prefix={<MessageOutlined />}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="Today"
              value={messageStats?.today || 0}
              prefix={<MessageOutlined />}
            />
          </Col>
        </Row>
        
        {/* Message Type Distribution */}
        <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
          <Col xs={24} sm={12} md={4}>
            <Card size="small">
              <Statistic
                title="Text"
                value={messageStats?.byType.text || 0}
                suffix={`${messageStats ? Math.round((messageStats.byType.text / messageStats.total) * 100) : 0}%`}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={4}>
            <Card size="small">
              <Statistic
                title="Images"
                value={messageStats?.byType.image || 0}
                suffix={`${messageStats ? Math.round((messageStats.byType.image / messageStats.total) * 100) : 0}%`}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={4}>
            <Card size="small">
              <Statistic
                title="Files"
                value={messageStats?.byType.file || 0}
                suffix={`${messageStats ? Math.round((messageStats.byType.file / messageStats.total) * 100) : 0}%`}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={4}>
            <Card size="small">
              <Statistic
                title="Audio"
                value={messageStats?.byType.audio || 0}
                suffix={`${messageStats ? Math.round((messageStats.byType.audio / messageStats.total) * 100) : 0}%`}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={4}>
            <Card size="small">
              <Statistic
                title="Video"
                value={messageStats?.byType.video || 0}
                suffix={`${messageStats ? Math.round((messageStats.byType.video / messageStats.total) * 100) : 0}%`}
              />
            </Card>
          </Col>
        </Row>
      </Card>

      {/* Call Statistics */}
      <Card title="Call Statistics" style={{ marginBottom: 24 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="Total Calls"
              value={callStats?.total || 0}
              prefix={<PhoneOutlined />}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="In Selected Range"
              value={callStats?.inRange || 0}
              prefix={<PhoneOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="Success Rate"
              value={callStats?.successRate || 0}
              suffix="%"
              prefix={<RiseOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="Avg Duration"
              value={callStats?.averageDuration || 0}
              suffix="min"
              prefix={<CalendarOutlined />}
            />
          </Col>
        </Row>
        
        {/* Call Status Distribution */}
        <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
          <Col xs={24} sm={8}>
            <Card size="small">
              <Statistic
                title="Completed"
                value={callStats?.byStatus.completed || 0}
                valueStyle={{ color: '#3f8600' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card size="small">
              <Statistic
                title="Failed"
                value={callStats?.byStatus.failed || 0}
                valueStyle={{ color: '#cf1322' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card size="small">
              <Statistic
                title="Canceled"
                value={callStats?.byStatus.canceled || 0}
                valueStyle={{ color: '#faad14' }}
              />
            </Card>
          </Col>
        </Row>
      </Card>

      {/* Charts Section */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} lg={12}>
          <Card title="Message Type Distribution">
            {messageStats && (
              <Pie
                data={[
                  { type: 'Text', value: messageStats.byType.text },
                  { type: 'Image', value: messageStats.byType.image },
                  { type: 'File', value: messageStats.byType.file },
                  { type: 'Audio', value: messageStats.byType.audio },
                  { type: 'Video', value: messageStats.byType.video },
                ]}
                angleField="value"
                colorField="type"
                radius={0.8}
                label={{
                  type: 'outer',
                  content: '{name}: {percentage}',
                }}
                height={300}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Call Status Distribution">
            {callStats && (
              <Pie
                data={[
                  { status: 'Completed', value: callStats.byStatus.completed },
                  { status: 'Failed', value: callStats.byStatus.failed },
                  { status: 'Canceled', value: callStats.byStatus.canceled },
                ]}
                angleField="value"
                colorField="status"
                radius={0.8}
                color={['#52c41a', '#ff4d4f', '#faad14']}
                label={{
                  type: 'outer',
                  content: '{name}: {percentage}',
                }}
                height={300}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* Message Hourly Chart */}
      {messageStats && messageStats.hourly && messageStats.hourly.length > 0 && (
        <Card title="Messages by Hour" style={{ marginBottom: 24 }}>
          <Line
            data={messageStats.hourly.map(h => ({ hour: `${h.hour}:00`, count: h.count }))}
            xField="hour"
            yField="count"
            point={{ size: 5, shape: 'circle' }}
            label={{
              style: {
                fill: '#aaa',
              },
            }}
            height={300}
          />
        </Card>
      )}

      {/* Top Users */}
      <Card title="Top Active Users">
        <Table
          dataSource={topUsers}
          columns={topUsersColumns}
          rowKey="_id"
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>
    </div>
  );
}
