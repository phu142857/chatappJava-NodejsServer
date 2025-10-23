import { useEffect, useState } from 'react';
import { App as AntdApp, Card, Table, Tag, Typography, Space, Button, Modal, Avatar } from 'antd';
import { PhoneOutlined, VideoCameraOutlined, UserOutlined } from '@ant-design/icons';
import apiClient from '../api/client';
import { API_BASE_URL } from '../config';

type CallParticipant = {
  userId: string | { _id: string } | null;
  username: string;
  avatar?: string;
  joinedAt: string;
  leftAt?: string;
  status: 'invited' | 'ringing' | 'connected' | 'declined' | 'missed' | 'left';
  isCaller: boolean;
};

type Call = {
  _id: string;
  callId: string;
  type: 'audio' | 'video';
  status: 'initiated' | 'ringing' | 'active' | 'ended' | 'declined' | 'missed' | 'cancelled';
  duration?: number;
  participants: CallParticipant[];
  startedAt: string;
  endedAt?: string;
  isGroupCall: boolean;
};

export default function Calls() {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<Call[]>([]);
  const [selectedCall, setSelectedCall] = useState<Call | null>(null);

  const resolveAvatarUrl = (avatar?: string) => {
    if (!avatar) return undefined;
    if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar;
    const base = API_BASE_URL.replace(/\/$/, '');
    // Avatar path from server is already in format /uploads/avatars/filename.jpg
    return `${base}${avatar}`;
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get<{ success: boolean; data: { calls: Call[] } }>('/calls/admin');
      setData(data?.data?.calls || []);
    } catch (error) {
      console.error('Failed to fetch calls:', error);
      message.error('Cannot load calls list');
      setData([]);
    } finally {
      setLoading(false);
    }
  };

  const formatDuration = (seconds?: number) => {
    if (!seconds) return 'N/A';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${minutes}:${secs.toString().padStart(2, '0')}`;
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'active': return 'green';
      case 'ended': return 'blue';
      case 'missed': return 'red';
      case 'declined': return 'orange';
      case 'cancelled': return 'gray';
      case 'initiated': return 'cyan';
      case 'ringing': return 'purple';
      default: return 'default';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'active': return 'Active';
      case 'ended': return 'Ended';
      case 'missed': return 'Missed';
      case 'declined': return 'Declined';
      case 'cancelled': return 'Cancelled';
      case 'initiated': return 'Initiated';
      case 'ringing': return 'Ringing';
      default: return status;
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  return (
    <Card title="Call Management">
      <Table
        rowKey="_id"
        loading={loading}
        dataSource={data}
        pagination={{ pageSize: 20 }}
        columns={[
          { title: 'ID', dataIndex: 'callId', width: 200 },
          { 
            title: 'Type', 
            render: (_, record) => (
              <Space>
                {record.type === 'video' ? <VideoCameraOutlined /> : <PhoneOutlined />}
                <span>{record.type === 'video' ? 'Video' : 'Audio'}</span>
                {record.isGroupCall && <Tag color="blue">Group</Tag>}
              </Space>
            )
          },
          { 
            title: 'Status', 
            dataIndex: 'status',
            render: (status) => (
              <Tag color={getStatusColor(status)}>
                {getStatusText(status)}
              </Tag>
            )
          },
          { 
            title: 'Duration', 
            dataIndex: 'duration',
            render: (duration) => formatDuration(duration)
          },
          { 
            title: 'Participants', 
            render: (_, record) => (
              <div>
                <Typography.Text strong>{record.participants.length} people</Typography.Text>
                <br/>
                <Button 
                  type="link" 
                  size="small"
                  onClick={() => setSelectedCall(record)}
                >
                  View Details
                </Button>
              </div>
            )
          },
          { 
            title: 'Start Time', 
            dataIndex: 'startedAt',
            render: (date) => new Date(date).toLocaleString()
          },
          { 
            title: 'End Time', 
            dataIndex: 'endedAt',
            render: (date) => date ? new Date(date).toLocaleString() : '-'
          }
        ]}
      />

      <Modal
        title={`Call Details - ${selectedCall?.callId}`}
        open={!!selectedCall}
        onCancel={() => setSelectedCall(null)}
        footer={null}
        width={600}
      >
        {selectedCall && (
          <div>
            <Space direction="vertical" style={{ width: '100%' }}>
              <div>
                <Typography.Text strong>Type:</Typography.Text> 
                <Space style={{ marginLeft: 8 }}>
                  {selectedCall.type === 'video' ? <VideoCameraOutlined /> : <PhoneOutlined />}
                  <span>{selectedCall.type === 'video' ? 'Video Call' : 'Audio Call'}</span>
                </Space>
              </div>
              
              <div>
                <Typography.Text strong>Status:</Typography.Text> 
                <Tag color={getStatusColor(selectedCall.status)} style={{ marginLeft: 8 }}>
                  {getStatusText(selectedCall.status)}
                </Tag>
              </div>
              
              <div>
                <Typography.Text strong>Duration:</Typography.Text> 
                <span style={{ marginLeft: 8 }}>{formatDuration(selectedCall.duration)}</span>
              </div>
              
              <div>
                <Typography.Text strong>Participants:</Typography.Text>
                <div style={{ marginTop: 8 }}>
                  {selectedCall.participants.map((participant, idx) => (
                    <div key={idx} style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      padding: 8, 
                      border: '1px solid #d9d9d9', 
                      borderRadius: 4, 
                      marginBottom: 8 
                    }}>
                      <Avatar size="small" src={resolveAvatarUrl(participant.avatar)} icon={<UserOutlined />} style={{ marginRight: 8 }} />
                      <div style={{ flex: 1 }}>
                        <Typography.Text strong>{participant.username}</Typography.Text>
                        <br/>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          ID: {participant.userId ? 
                            (typeof participant.userId === 'object' ? 
                              (participant.userId as any)._id || String(participant.userId) : 
                              String(participant.userId)
                            ) : 'N/A'
                          }
                        </Typography.Text>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <Tag color={
                          participant.status === 'connected' ? 'green' :
                          participant.status === 'declined' || participant.status === 'missed' ? 'red' :
                          participant.status === 'left' ? 'orange' :
                          'blue'
                        }>
                          {participant.status === 'connected' ? 'Connected' :
                           participant.status === 'declined' ? 'Declined' :
                           participant.status === 'missed' ? 'Missed' :
                           participant.status === 'left' ? 'Left' :
                           participant.status === 'ringing' ? 'Ringing' :
                           'Invited'}
                        </Tag>
                        {participant.isCaller && (
                          <Tag color="gold" style={{ marginLeft: 4 }}>Caller</Tag>
                        )}
                        <br/>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {participant.joinedAt ? 
                            `Tham gia: ${new Date(participant.joinedAt).toLocaleString()}` :
                            'Not joined'
                          }
                          {participant.leftAt && (
                            <><br/>Left: {new Date(participant.leftAt).toLocaleString()}</>
                          )}
                        </Typography.Text>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </Space>
          </div>
        )}
      </Modal>
    </Card>
  );
}
